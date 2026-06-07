package com.zara.assistant.core

import android.content.Context
import com.zara.assistant.actions.ContactResolver
import com.zara.assistant.actions.RuleBasedAppResolver
import com.zara.assistant.models.ClarificationCandidate
import com.zara.assistant.models.ClarificationEntityType
import com.zara.assistant.models.PendingClarification
import com.zara.assistant.utils.ZaraLogger
import java.util.UUID

/**
 * Layer 5.1 + 5 Hardening — Entity Resolver.
 *
 * Improvements:
 * - ContactNormalizer applied to query AND contact names
 * - ContactDeduplicationEngine eliminates duplicates before clarification
 * - ContactRankingEngine sorts candidates by score
 * - EntityConfidenceEvaluator gates execution
 * - PreferredAppRegistry biases app lookup
 * - LOW confidence → no execution
 * - ClarificationManager stores candidates with stable resolvedValue (phone/package)
 */
class EntityResolver(private val context: Context) {

    private val contactResolver = ContactResolver(context)
    private val appResolver     = RuleBasedAppResolver()

    private val suffixes = setOf(
        "bro", "dude", "anna", "akka", "machi", "machan", "boss", "sis", "friend", "frnd"
    )

    @Volatile private var appCache: Map<String, String>? = null

    suspend fun resolve(intent: ZaraIntent): ZaraIntent {
        var current = intent
        if (current.extra.containsKey(IntentExtra.RECIPIENT)) current = resolveContact(current)
        if (current.extra.containsKey(IntentExtra.APP))       current = resolveApp(current)
        return current
    }

    // ── Contact resolution ─────────────────────────────────────────────────
    private suspend fun resolveContact(intent: ZaraIntent): ZaraIntent {
        val raw   = intent.extra[IntentExtra.RECIPIENT] ?: return intent
        val query = ContactNormalizer.normalize(stripSuffix(raw))

        val rawAll = contactResolver.resolveAll(query)
        if (rawAll.isEmpty()) {
            ZaraLogger.d("EntityResolver: no contact for '$query'")
            return intent
        }

        // Deduplicate
        val deduped = ContactDeduplicationEngine.deduplicate(rawAll)

        // Rank
        val ranked = ContactRankingEngine.rank(query, deduped)

        val newExtra = intent.extra.toMutableMap()

        if (ranked.size == 1) {
            // Single result after dedup → auto-resolve
            val normName = ContactNormalizer.normalize(ranked[0].displayName)
            val topScore = if (normName == query) 100 else if (normName.startsWith(query)) 80 else 60
            val level = EntityConfidenceEvaluator.evaluate(1, topScore)
            if (level == EntityConfidenceLevel.LOW) {
                ZaraLogger.d("EntityResolver: LOW confidence, skipping contact")
                return intent
            }
            newExtra[IntentExtra.CONTACT_NAME]      = ranked[0].displayName
            newExtra[IntentExtra.PHONE_NUMBER]      = ranked[0].number
            newExtra[IntentExtra.ENTITY_CONFIDENCE] = if (level == EntityConfidenceLevel.HIGH) "1.0" else "0.7"
        } else {
            // Exact normalized match wins without clarification
            val exact = ranked.firstOrNull { ContactNormalizer.normalize(it.displayName) == query }
            if (exact != null) {
                newExtra[IntentExtra.CONTACT_NAME]      = exact.displayName
                newExtra[IntentExtra.PHONE_NUMBER]      = exact.number
                newExtra[IntentExtra.ENTITY_CONFIDENCE] = "1.0"
            } else {
                // Build clarification with stable resolvedValue = phone number
                val candidates = ranked.take(5).map {
                    ClarificationCandidate(
                        displayName   = it.displayName,
                        resolvedValue = it.number,
                        confidence    = 0.7f
                    )
                }
                val clarification = PendingClarification(
                    clarificationId = UUID.randomUUID().toString(),
                    originalIntent  = intent,
                    entityType      = ClarificationEntityType.CONTACT,
                    candidates      = candidates
                )
                ClarificationManager.store(clarification)
                newExtra[IntentExtra.NEEDS_CLARIFICATION] = "true"
                newExtra[IntentExtra.ENTITY_CANDIDATES]   = candidates.joinToString("|") { it.displayName }
                newExtra[IntentExtra.ENTITY_CONFIDENCE]   = "0.5"
            }
        }
        return intent.copy(extra = newExtra)
    }

    // ── App resolution ──────────────────────────────────────────────────
    private fun resolveApp(intent: ZaraIntent): ZaraIntent {
        val rawQuery = intent.extra[IntentExtra.APP] ?: return intent
        // Apply preferred app registry before lookup
        val appQuery = PreferredAppRegistry.preferred(rawQuery)
        val cache    = getAppCache()
        val result   = appResolver.resolve(appQuery, cache)

        if (result.packageName == null && result.candidates.isEmpty()) return intent

        val newExtra = intent.extra.toMutableMap()
        if (result.packageName != null) {
            newExtra[IntentExtra.APP_PACKAGE]       = result.packageName
            newExtra[IntentExtra.APP_NAME]          = result.displayLabel ?: appQuery
            newExtra[IntentExtra.ENTITY_CONFIDENCE] = result.confidence.toString()
        } else {
            newExtra[IntentExtra.NEEDS_CLARIFICATION] = "true"
            newExtra[IntentExtra.ENTITY_CANDIDATES]   = result.candidates.take(5).joinToString("|")
            newExtra[IntentExtra.ENTITY_CONFIDENCE]   = "0.0"
        }
        return intent.copy(extra = newExtra)
    }

    // ── Suffix stripping ─────────────────────────────────────────────────
    private fun stripSuffix(name: String): String {
        val parts = name.lowercase().trim().split(" ")
        return if (parts.size >= 2 && suffixes.contains(parts.last())) parts.dropLast(1).joinToString(" ") else parts.joinToString(" ")
    }

    // ── App cache ────────────────────────────────────────────────────────
    private fun getAppCache(): Map<String, String> {
        appCache?.let { return it }
        val pm    = context.packageManager
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
