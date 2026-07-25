package com.zara.assistant.models

import com.zara.assistant.core.ZaraIntent

/**
 * Layer 5.3 / 6 — Clarification model.
 * Session-only. No persistence.
 *
 * Phase 3: added AMPM entity type for reminder AM/PM disambiguation.
 * ClarificationManager.resolve() handles AMPM by stamping AMPM_HINT
 * ("am" or "pm") onto the originalIntent and returning it for re-execution.
 */
data class ClarificationCandidate(
    val displayName: String,
    val resolvedValue: String,  // phone for contacts, package for apps, resolved text for CONTEXT, "am"/"pm" for AMPM
    val confidence: Float = 1.0f
)

enum class ClarificationEntityType {
    CONTACT,
    APP,
    CONTEXT,  // Layer 6: context confirmation (resolvedValue = rewritten text to classify)
    AMPM      // Phase 3: AM/PM disambiguation for reminder times (resolvedValue = "am" or "pm")
}

data class PendingClarification(
    val clarificationId: String,
    val originalIntent: ZaraIntent,
    val entityType: ClarificationEntityType,
    val candidates: List<ClarificationCandidate>,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = createdAt + TIMEOUT_MS,
    val attemptCount: Int = 0
) {
    companion object {
        const val TIMEOUT_MS = 30_000L
    }
    fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
}
