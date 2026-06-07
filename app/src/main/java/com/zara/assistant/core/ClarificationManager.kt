package com.zara.assistant.core

import com.zara.assistant.models.ClarificationCandidate
import com.zara.assistant.models.ClarificationEntityType
import com.zara.assistant.models.PendingClarification
import java.util.UUID

/**
 * Layer 5.3 + 5 Hardening + Layer 6 Architecture Fix
 *
 * ClarificationManager is the SOLE clarification authority.
 * Handles CONTACT, APP, and CONTEXT (Layer 6) entity types.
 *
 * For CONTEXT type:
 *   resolvedContextText() returns the pending resolved text if user confirmed.
 *   VoiceSessionManager checks this before running the normal pipeline.
 */
object ClarificationManager {

    private var pending: PendingClarification? = null
    private val cancelWords = setOf("cancel", "stop", "never mind", "nevermind")

    // Layer 6: holds resolved text after CONTEXT confirmation; read once then cleared
    private var confirmedContextText: String? = null

    fun store(clarification: PendingClarification) { pending = clarification }

    fun hasPending(): Boolean {
        val p = pending ?: return false
        if (p.isExpired()) { pending = null; return false }
        return true
    }

    fun clear() { pending = null; confirmedContextText = null }

    /**
     * Layer 6: returns confirmed resolved text and clears it (read-once).
     * VoiceSessionManager checks this after ClarificationManager.resolve() returns null
     * to detect CONTEXT confirmations.
     */
    fun popConfirmedContextText(): String? {
        val t = confirmedContextText
        confirmedContextText = null
        return t
    }

    fun resolve(userText: String): ZaraIntent? {
        val p = pending ?: return null
        if (p.isExpired()) { pending = null; return null }

        val trimmed = userText.trim().lowercase()

        if (cancelWords.any { trimmed.contains(it) }) { pending = null; return null }

        pending = p.copy(attemptCount = p.attemptCount + 1)

        // CONTEXT type: yes/no confirmation for Layer 6
        if (p.entityType == ClarificationEntityType.CONTEXT) {
            val yesWords = setOf("yes", "yeah", "yep", "sure", "ok", "okay", "correct", "right")
            return when {
                yesWords.any { trimmed == it || trimmed.startsWith("$it ") } -> {
                    val resolved = p.candidates.firstOrNull()?.resolvedValue
                    pending = null
                    if (resolved != null) {
                        confirmedContextText = resolved
                    }
                    null  // VoiceSessionManager checks popConfirmedContextText()
                }
                cancelWords.any { trimmed.contains(it) } -> {
                    pending = null; null
                }
                else -> {
                    // Not a confirmation — abandon context clarification, treat as new command
                    pending = null; null
                }
            }
        }

        // CONTACT / APP: existing logic
        val storedCandidates = p.candidates

        val selected: ClarificationCandidate? = when {
            trimmed.matches(Regex("\\d+")) -> storedCandidates.getOrNull(trimmed.toInt() - 1)
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
                newExtra[IntentExtra.PHONE_NUMBER]      = selected.resolvedValue
                newExtra[IntentExtra.ENTITY_CONFIDENCE] = selected.confidence.toString()
            }
            ClarificationEntityType.APP -> {
                newExtra[IntentExtra.APP_PACKAGE]       = selected.resolvedValue
                newExtra[IntentExtra.APP_NAME]          = selected.displayName
                newExtra[IntentExtra.ENTITY_CONFIDENCE] = selected.confidence.toString()
            }
            ClarificationEntityType.CONTEXT -> { /* handled above */ }
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
