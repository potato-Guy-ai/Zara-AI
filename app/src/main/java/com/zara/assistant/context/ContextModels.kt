package com.zara.assistant.context

/**
 * Batch A1 — Fix 5 (Context Expiration) + Fix 6 (Confidence Decay)
 *
 * FIX 5: CONTEXT_EXPIRY_PERSON_MS reduced from 5 minutes to 45 seconds.
 *   After 45s, context is expired and follow-up pronouns will not auto-resolve.
 *
 * FIX 6: Confidence decay over time.
 *   0–15s   → HIGH
 *   15–45s  → MEDIUM (triggers clarification, not auto-execute)
 *   >45s    → expired (isExpired() = true)
 *   Decay is computed on read (currentConfidence()), not stored.
 *   Stored confidence is always the initial value at store-time.
 *
 * Session-only, in-memory, no persistence.
 */

const val CONTEXT_EXPIRY_PERSON_MS  = 45_000L   // FIX 5: was 5 * 60 * 1000L
const val CONTEXT_EXPIRY_MEDIA_MS   = 45_000L   // FIX 5: was 5 * 60 * 1000L
const val CONTEXT_EXPIRY_QUERY_MS   = 45_000L   // FIX 5: was 5 * 60 * 1000L
const val CONTEXT_EXPIRY_APP_MS     = 60_000L   // FIX 5: was 10 * 60 * 1000L
const val CONTEXT_EXPIRY_ACTION_MS  = 60_000L   // FIX 5: was 10 * 60 * 1000L

// FIX 6: decay thresholds
private const val DECAY_HIGH_MS   = 15_000L  // 0–15s → HIGH
private const val DECAY_MEDIUM_MS = 45_000L  // 15–45s → MEDIUM

enum class ContextConfidence { HIGH, MEDIUM, LOW }

/**
 * FIX 6: Compute decayed confidence from timestamp.
 * Does not modify stored fields — read-only computation.
 */
fun decayedConfidence(timestamp: Long, expiryMs: Long): ContextConfidence {
    val age = System.currentTimeMillis() - timestamp
    return when {
        age >= expiryMs          -> ContextConfidence.LOW   // expired (caller checks isExpired first)
        age >= DECAY_MEDIUM_MS   -> ContextConfidence.MEDIUM
        age >= DECAY_HIGH_MS     -> ContextConfidence.MEDIUM
        else                     -> ContextConfidence.HIGH
    }
}

data class PersonContext(
    val contactName: String,
    val phoneNumber: String?,
    val confidence: ContextConfidence,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isExpired() = System.currentTimeMillis() - timestamp > CONTEXT_EXPIRY_PERSON_MS
    /** FIX 6: returns time-decayed confidence, not the stored value */
    val currentConfidence: ContextConfidence get() = decayedConfidence(timestamp, CONTEXT_EXPIRY_PERSON_MS)
}

data class AppContext(
    val appName: String,
    val packageName: String?,
    val confidence: ContextConfidence,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isExpired() = System.currentTimeMillis() - timestamp > CONTEXT_EXPIRY_APP_MS
    val currentConfidence: ContextConfidence get() = decayedConfidence(timestamp, CONTEXT_EXPIRY_APP_MS)
}

data class ActionContext(
    val lastAction: String,
    val lastTarget: String?,
    val confidence: ContextConfidence,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isExpired() = System.currentTimeMillis() - timestamp > CONTEXT_EXPIRY_ACTION_MS
    val currentConfidence: ContextConfidence get() = decayedConfidence(timestamp, CONTEXT_EXPIRY_ACTION_MS)
}

data class QueryContext(
    val query: String,
    val queryType: String,
    val confidence: ContextConfidence,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isExpired() = System.currentTimeMillis() - timestamp > CONTEXT_EXPIRY_QUERY_MS
    val currentConfidence: ContextConfidence get() = decayedConfidence(timestamp, CONTEXT_EXPIRY_QUERY_MS)
}

data class MediaContext(
    val song: String?,
    val artist: String?,
    val playlist: String?,
    val video: String?,
    val confidence: ContextConfidence,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isExpired() = System.currentTimeMillis() - timestamp > CONTEXT_EXPIRY_MEDIA_MS
    val currentConfidence: ContextConfidence get() = decayedConfidence(timestamp, CONTEXT_EXPIRY_MEDIA_MS)
}
