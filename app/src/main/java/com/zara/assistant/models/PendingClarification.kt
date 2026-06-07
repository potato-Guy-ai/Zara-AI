package com.zara.assistant.models

import com.zara.assistant.core.ZaraIntent

/**
 * Layer 5.3 / 6 — Clarification model.
 * Session-only. No persistence.
 */
data class ClarificationCandidate(
    val displayName: String,
    val resolvedValue: String,  // phone for contacts, package for apps, resolved text for CONTEXT
    val confidence: Float = 1.0f
)

enum class ClarificationEntityType {
    CONTACT,
    APP,
    CONTEXT   // Layer 6: context confirmation (resolvedValue = rewritten text to classify)
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
