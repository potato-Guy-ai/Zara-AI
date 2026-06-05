package com.zara.assistant.core

import android.content.Context
import com.zara.assistant.actions.AppActions
import com.zara.assistant.actions.ContactResolver
import com.zara.assistant.actions.RuleBasedAppResolver
import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 5.1 — Entity Resolution.
 *
 * Converts raw extracted slots into resolved real-world entities.
 * Runs after SlotExtractor, before IntentRouter.
 *
 * - RECIPIENT → CONTACT_NAME + PHONE_NUMBER
 * - APP       → APP_PACKAGE  + APP_NAME
 *
 * Rules:
 * - Never overwrites intent.target
 * - Additive only (writes into extra)
 * - Deterministic, no ML, no network, no background threads
 * - Contact cache loaded once per instance
 */
class EntityResolver(private val context: Context) {

    private val contactResolver = ContactResolver(context)
    private val appResolver     = RuleBasedAppResolver()

    // Suffix tokens stripped before contact matching (safe, culture-aware)
    private val suffixes = setOf(
        "bro", "dude", "anna", "akka", "machi", "machan", "boss", "sis", "friend", "frnd"
    )

    // App cache: loaded lazily, then reused
    @Volatile private var appCache: Map<String, String>? = null

    fun resolve(intent: ZaraIntent): ZaraIntent {
        var current = intent
        if (current.extra.containsKey(IntentExtra.RECIPIENT)) {
            current = resolveContact(current)
        }
        if (current.extra.containsKey(IntentExtra.APP)) {
            current = resolveApp(current)
        }
        return current
    }

    // ── Contact resolution ───────────────────────────────────────────────
    private fun resolveContact(intent: ZaraIntent): ZaraIntent {
        val raw = intent.extra[IntentExtra.RECIPIENT] ?: return intent
        val query = stripSuffix(raw.lowercase().trim())

        val all = contactResolver.resolveAll(query)
        if (all.isEmpty()) {
            ZaraLogger.d("EntityResolver: no contact found for '$query'")
            return intent
        }

        val newExtra = intent.extra.toMutableMap()

        if (all.size == 1) {
            newExtra[IntentExtra.CONTACT_NAME]      = all[0].displayName
            newExtra[IntentExtra.PHONE_NUMBER]      = all[0].number
            newExtra[IntentExtra.ENTITY_CONFIDENCE] = "1.0"
        } else {
            // Exact display name match wins
            val exact = all.firstOrNull { it.displayName.lowercase() == query }
            if (exact != null) {
                newExtra[IntentExtra.CONTACT_NAME]      = exact.displayName
                newExtra[IntentExtra.PHONE_NUMBER]      = exact.number
                newExtra[IntentExtra.ENTITY_CONFIDENCE] = "1.0"
            } else {
                // Multiple candidates — surface for clarification
                newExtra[IntentExtra.NEEDS_CLARIFICATION] = "true"
                newExtra[IntentExtra.ENTITY_CANDIDATES]   =
                    all.take(5).joinToString("|") { it.displayName }
                newExtra[IntentExtra.ENTITY_CONFIDENCE]   = "0.0"
            }
        }
        return intent.copy(extra = newExtra)
    }

    // ── App resolution ───────────────────────────────────────────────────
    private fun resolveApp(intent: ZaraIntent): ZaraIntent {
        val appQuery = intent.extra[IntentExtra.APP] ?: return intent
        val cache = getAppCache()
        val result = appResolver.resolve(appQuery.lowercase().trim(), cache)

        if (result.packageName == null && result.candidates.isEmpty()) return intent

        val newExtra = intent.extra.toMutableMap()

        if (result.packageName != null) {
            newExtra[IntentExtra.APP_PACKAGE]       = result.packageName
            newExtra[IntentExtra.APP_NAME]          = result.displayLabel ?: appQuery
            newExtra[IntentExtra.ENTITY_CONFIDENCE] = result.confidence.toString()
        } else if (result.candidates.isNotEmpty()) {
            newExtra[IntentExtra.NEEDS_CLARIFICATION] = "true"
            newExtra[IntentExtra.ENTITY_CANDIDATES]   = result.candidates.take(5).joinToString("|")
            newExtra[IntentExtra.ENTITY_CONFIDENCE]   = "0.0"
        }
        return intent.copy(extra = newExtra)
    }

    // ── Suffix stripping ─────────────────────────────────────────────────
    private fun stripSuffix(name: String): String {
        val parts = name.split(" ")
        if (parts.size >= 2 && suffixes.contains(parts.last())) {
            return parts.dropLast(1).joinToString(" ")
        }
        return name
    }

    // ── App cache ────────────────────────────────────────────────────────
    private fun getAppCache(): Map<String, String> {
        appCache?.let { return it }
        val pm = context.packageManager
        val cache = mutableMapOf<String, String>()
        pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .forEach { info ->
                val label = pm.getApplicationLabel(info).toString().lowercase().trim()
                if (!cache.containsKey(label)) cache[label] = info.packageName
            }
        appCache = cache
        return cache
    }
}
