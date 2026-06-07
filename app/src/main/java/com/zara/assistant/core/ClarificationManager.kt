package com.zara.assistant.core

import com.zara.assistant.models.ClarificationCandidate
import com.zara.assistant.models.ClarificationEntityType
import com.zara.assistant.models.PendingClarification
import java.util.UUID

/**
 * Layer 5.3 + 5 Hardening — Clarification Manager.
 *
 * Fix: candidate identity is now stable.
 * Selection resolves by candidate index from stored list — NOT by re-matching display name after sort.
 * Numeric "1" always maps to candidates[0], "2" to candidates[1], etc.
 * Exact and contains match also operate on the immutable stored list.
 */
object ClarificationManager {

    private var pending: PendingClarification? = null
    private val cancelWords = setOf("cancel", "stop", "never mind", "nevermind")

    fun store(clarification: PendingClarification) { pending = clarification }

    fun hasPending(): Boolean {
        val p = pending ?: return false
        if (p.isExpired()) { pending = null; return false }
        return true
    }

    fun clear() { pending = null }

    fun resolve(userText: String): ZaraIntent? {
        val p = pending ?: return null
        if (p.isExpired()) { pending = null; return null }

        val trimmed = userText.trim().lowercase()

        if (cancelWords.any { trimmed.contains(it) }) { pending = null; return null }

        pending = p.copy(attemptCount = p.attemptCount + 1)

        // HARDENING FIX: resolve against STORED ordered list — identity preserved
        val storedCandidates = p.candidates  // immutable order

        val selected: ClarificationCandidate? = when {
            trimmed.matches(Regex("\\d+")) -> {
                val idx = trimmed.toInt() - 1
                storedCandidates.getOrNull(idx)   // exact index — never re-sorted
            }
            storedCandidates.any { ContactNormalizer.normalize(it.displayName) == trimmed } ->
                storedCandidates.first { ContactNormalizer.normalize(it.displayName) == trimmed }
            storedCandidates.any { ContactNormalizer.normalize(it.displayName).contains(trimmed) } ->
                storedCandidates.first { ContactNormalizer.normalize(it.displayName).contains(trimmed) }
            else -> null
        }

        if (selected == null) return null

        val newExtra = p.originalIntent.extra.toMutableMap()
        newExtra.remove(IntentExtra.NEEDS_CLARIFICATION)
        newExtra.remove(IntentExtra.ENTITY_CANDIDATES)

        when (p.entityType) {
            ClarificationEntityType.CONTACT -> {
                newExtra[IntentExtra.CONTACT_NAME]      = selected.displayName
                newExtra[IntentExtra.PHONE_NUMBER]      = selected.resolvedValue  // stable phone stored at creation
                newExtra[IntentExtra.ENTITY_CONFIDENCE] = selected.confidence.toString()
            }
            ClarificationEntityType.APP -> {
                newExtra[IntentExtra.APP_PACKAGE]       = selected.resolvedValue
                newExtra[IntentExtra.APP_NAME]          = selected.displayName
                newExtra[IntentExtra.ENTITY_CONFIDENCE] = selected.confidence.toString()
            }
        }

        ExecutionTelemetry.record(
            intent          = p.originalIntent.action,
            resolvedEntity  = selected.displayName,
            confidence      = selected.confidence.toString(),
            selectedContact = if (p.entityType == ClarificationEntityType.CONTACT) selected.displayName else null,
            selectedApp     = if (p.entityType == ClarificationEntityType.APP) selected.displayName else null,
            executionResult = "clarification_resolved"
        )

        pending = null
        return p.originalIntent.copy(extra = newExtra)
    }

    fun buildFromContactIntent(intent: ZaraIntent, candidates: List<Pair<String, String>>): PendingClarification =
        PendingClarification(
            clarificationId = UUID.randomUUID().toString(),
            originalIntent  = intent,
            entityType      = ClarificationEntityType.CONTACT,
            candidates      = candidates.map { ClarificationCandidate(it.first, it.second) }
        )

    fun buildFromAppIntent(intent: ZaraIntent, candidates: List<Pair<String, String>>): PendingClarification =
        PendingClarification(
            clarificationId = UUID.randomUUID().toString(),
            originalIntent  = intent,
            entityType      = ClarificationEntityType.APP,
            candidates      = candidates.map { ClarificationCandidate(it.first, it.second) }
        )
}
