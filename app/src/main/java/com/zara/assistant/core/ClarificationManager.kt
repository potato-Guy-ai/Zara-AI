package com.zara.assistant.core

import com.zara.assistant.models.ClarificationCandidate
import com.zara.assistant.models.ClarificationEntityType
import com.zara.assistant.models.PendingClarification
import java.util.UUID

/**
 * Layer 5.3 — Manages one active clarification at a time.
 * Session-only, no persistence.
 */
object ClarificationManager {

    private var pending: PendingClarification? = null

    private val cancelWords = setOf("cancel", "stop", "never mind", "nevermind")

    fun store(clarification: PendingClarification) {
        pending = clarification
    }

    fun hasPending(): Boolean {
        val p = pending ?: return false
        if (p.isExpired()) { pending = null; return false }
        return true
    }

    fun clear() { pending = null }

    /**
     * Try to resolve user reply against active clarification.
     * Returns resolved ZaraIntent ready for execution, or null if unresolved.
     */
    fun resolve(userText: String): ZaraIntent? {
        val p = pending ?: return null

        if (p.isExpired()) { pending = null; return null }

        val trimmed = userText.trim().lowercase()

        // Cancel
        if (cancelWords.any { trimmed.contains(it) }) {
            pending = null
            return null
        }

        // Increment attempt
        pending = p.copy(attemptCount = p.attemptCount + 1)

        val selected: ClarificationCandidate? = when {
            // Numeric: "1", "2", "3"
            trimmed.matches(Regex("\\d+")) -> {
                val idx = trimmed.toInt() - 1
                p.candidates.getOrNull(idx)
            }
            // Exact match
            p.candidates.any { it.displayName.lowercase() == trimmed } ->
                p.candidates.first { it.displayName.lowercase() == trimmed }
            // Contains match
            p.candidates.any { it.displayName.lowercase().contains(trimmed) } ->
                p.candidates.first { it.displayName.lowercase().contains(trimmed) }
            else -> null
        }

        if (selected == null) return null

        // Build resolved intent
        val newExtra = p.originalIntent.extra.toMutableMap()
        newExtra.remove(IntentExtra.NEEDS_CLARIFICATION)
        newExtra.remove(IntentExtra.ENTITY_CANDIDATES)

        when (p.entityType) {
            ClarificationEntityType.CONTACT -> {
                newExtra[IntentExtra.CONTACT_NAME]      = selected.displayName
                newExtra[IntentExtra.PHONE_NUMBER]      = selected.resolvedValue
                newExtra[IntentExtra.ENTITY_CONFIDENCE] = selected.confidence.toString()
            }
            ClarificationEntityType.APP -> {
                newExtra[IntentExtra.APP_PACKAGE]       = selected.resolvedValue
                newExtra[IntentExtra.APP_NAME]          = selected.displayName
                newExtra[IntentExtra.ENTITY_CONFIDENCE] = selected.confidence.toString()
            }
        }

        pending = null
        return p.originalIntent.copy(extra = newExtra)
    }

    /** Build a PendingClarification from NEEDS_CLARIFICATION intent for contacts. */
    fun buildFromContactIntent(
        intent: ZaraIntent,
        candidates: List<Pair<String, String>> // displayName to phoneNumber
    ): PendingClarification = PendingClarification(
        clarificationId = UUID.randomUUID().toString(),
        originalIntent  = intent,
        entityType      = ClarificationEntityType.CONTACT,
        candidates      = candidates.map {
            com.zara.assistant.models.ClarificationCandidate(it.first, it.second)
        }
    )

    /** Build a PendingClarification from NEEDS_CLARIFICATION intent for apps. */
    fun buildFromAppIntent(
        intent: ZaraIntent,
        candidates: List<Pair<String, String>> // displayName to packageName
    ): PendingClarification = PendingClarification(
        clarificationId = UUID.randomUUID().toString(),
        originalIntent  = intent,
        entityType      = ClarificationEntityType.APP,
        candidates      = candidates.map {
            com.zara.assistant.models.ClarificationCandidate(it.first, it.second)
        }
    )
}
