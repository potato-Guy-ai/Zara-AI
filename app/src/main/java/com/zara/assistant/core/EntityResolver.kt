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
 * Layer 5.1 + 5 Hardening + Final Safety Fixes + Strong-Candidate Fix
 *
 * FIX 1: MEDIUM confidence single result forces clarification.
 * FIX 2: resolvedValue = phone number always.
 * FIX 3 (strong-candidate): In the multiple-results branch, compute strongCandidates
 *   (score >= STRONG_THRESHOLD=80). If strongCandidates.size > 1, do NOT auto-resolve
 *   on exact match — trigger clarification using only strong candidates (excludes score-60).
 *   If strongCandidates.size == 1, auto-resolve to that one candidate.
 *   Falls back to existing logic when no strong candidates exist.
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

    private suspend fun resolveContact(intent: ZaraIntent): ZaraIntent {
        val raw   = intent.extra[IntentExtra.RECIPIENT] ?: return intent
        val query = ContactNormalizer.normalize(stripSuffix(raw))

        val rawAll = contactResolver.resolveAll(query)
        if (rawAll.isEmpty()) {
            ZaraLogger.d("EntityResolver: no contact for \'$query\'")
            return intent
        }

        val deduped = ContactDeduplicationEngine.deduplicate(rawAll)
        val ranked  = ContactRankingEngine.rank(query, deduped)
        val newExtra = intent.extra.toMutableMap()

        if (ranked.size == 1) {
            val normName = ContactNormalizer.normalize(ranked[0].displayName)
            val topScore = ContactRankingEngine.scoreOf(query, normName)
            val level = EntityConfidenceEvaluator.evaluate(1, topScore)

            when (level) {
                EntityConfidenceLevel.HIGH -> {
                    newExtra[IntentExtra.CONTACT_NAME]      = ranked[0].displayName
                    newExtra[IntentExtra.PHONE_NUMBER]      = ranked[0].number
                    newExtra[IntentExtra.ENTITY_CONFIDENCE] = "1.0"
                    return intent.copy(extra = newExtra)
                }
                EntityConfidenceLevel.MEDIUM -> {
                    val candidates = ranked.map {
                        ClarificationCandidate(
                            displayName   = it.displayName,
                            resolvedValue = it.number,
                            confidence    = 0.65f
                        )
                    }
                    ClarificationManager.store(
                        PendingClarification(
                            clarificationId = UUID.randomUUID().toString(),
                            originalIntent  = intent,
                            entityType      = ClarificationEntityType.CONTACT,
                            candidates      = candidates
                        )
                    )
                    newExtra[IntentExtra.NEEDS_CLARIFICATION] = "true"
                    newExtra[IntentExtra.ENTITY_CANDIDATES]   = candidates.joinToString("|") { it.displayName }
                    newExtra[IntentExtra.ENTITY_CONFIDENCE]   = "0.65"
                    return intent.copy(extra = newExtra)
                }
                EntityConfidenceLevel.LOW -> {
                    ZaraLogger.d("EntityResolver: LOW confidence, skipping")
                    return intent
                }
            }
        }

        // ── Multiple results ──────────────────────────────────────────────────
        // FIX 3: compute strong candidates (score >= 80) from the ranked list.
        val normQuery = query
        val strongCandidates = ranked.filter { c ->
            ContactRankingEngine.scoreOf(normQuery, ContactNormalizer.normalize(c.displayName)) >= ContactRankingEngine.STRONG_THRESHOLD
        }

        when {
            // 2+ strong candidates → always clarify, show only strong candidates
            strongCandidates.size > 1 -> {
                val candidates = strongCandidates.map {
                    ClarificationCandidate(
                        displayName   = it.displayName,
                        resolvedValue = it.number,
                        confidence    = 0.8f
                    )
                }
                ClarificationManager.store(
                    PendingClarification(
                        clarificationId = UUID.randomUUID().toString(),
                        originalIntent  = intent,
                        entityType      = ClarificationEntityType.CONTACT,
                        candidates      = candidates
                    )
                )
                newExtra[IntentExtra.NEEDS_CLARIFICATION] = "true"
                newExtra[IntentExtra.ENTITY_CANDIDATES]   = candidates.joinToString("|") { it.displayName }
                newExtra[IntentExtra.ENTITY_CONFIDENCE]   = "0.8"
            }

            // Exactly 1 strong candidate → auto-resolve
            strongCandidates.size == 1 -> {
                newExtra[IntentExtra.CONTACT_NAME]      = strongCandidates[0].displayName
                newExtra[IntentExtra.PHONE_NUMBER]      = strongCandidates[0].number
                newExtra[IntentExtra.ENTITY_CONFIDENCE] = "1.0"
            }

            // No strong candidates → fall back to existing exact-match / clarification logic
            else -> {
                val exact = ranked.firstOrNull { ContactNormalizer.normalize(it.displayName) == normQuery }
                if (exact != null) {
                    newExtra[IntentExtra.CONTACT_NAME]      = exact.displayName
                    newExtra[IntentExtra.PHONE_NUMBER]      = exact.number
                    newExtra[IntentExtra.ENTITY_CONFIDENCE] = "1.0"
                } else {
                    val candidates = ranked.take(5).map {
                        ClarificationCandidate(
                            displayName   = it.displayName,
                            resolvedValue = it.number,
                            confidence    = 0.7f
                        )
                    }
                    ClarificationManager.store(
                        PendingClarification(
                            clarificationId = UUID.randomUUID().toString(),
                            originalIntent  = intent,
                            entityType      = ClarificationEntityType.CONTACT,
                            candidates      = candidates
                        )
                    )
                    newExtra[IntentExtra.NEEDS_CLARIFICATION] = "true"
                    newExtra[IntentExtra.ENTITY_CANDIDATES]   = candidates.joinToString("|") { it.displayName }
                    newExtra[IntentExtra.ENTITY_CONFIDENCE]   = "0.5"
                }
            }
        }
        return intent.copy(extra = newExtra)
    }

    private fun resolveApp(intent: ZaraIntent): ZaraIntent {
        val rawQuery = intent.extra[IntentExtra.APP] ?: return intent
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

    private fun stripSuffix(name: String): String {
        val parts = name.lowercase().trim().split(" ")
        return if (parts.size >= 2 && suffixes.contains(parts.last())) parts.dropLast(1).joinToString(" ") else parts.joinToString(" ")
    }

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
