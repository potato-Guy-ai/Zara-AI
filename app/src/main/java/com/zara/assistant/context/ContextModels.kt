package com.zara.assistant.context

/**
 * Layer 6.0 — Session context data models.
 * Session-only, in-memory, no persistence.
 */

const val CONTEXT_EXPIRY_PERSON_MS  = 5  * 60 * 1000L
const val CONTEXT_EXPIRY_MEDIA_MS   = 5  * 60 * 1000L
const val CONTEXT_EXPIRY_QUERY_MS   = 5  * 60 * 1000L
const val CONTEXT_EXPIRY_APP_MS     = 10 * 60 * 1000L
const val CONTEXT_EXPIRY_ACTION_MS  = 10 * 60 * 1000L

enum class ContextConfidence { HIGH, MEDIUM, LOW }

data class PersonContext(
    val contactName: String,
    val phoneNumber: String?,
    val confidence: ContextConfidence,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isExpired() = System.currentTimeMillis() - timestamp > CONTEXT_EXPIRY_PERSON_MS
}

data class AppContext(
    val appName: String,
    val packageName: String?,
    val confidence: ContextConfidence,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isExpired() = System.currentTimeMillis() - timestamp > CONTEXT_EXPIRY_APP_MS
}

data class ActionContext(
    val lastAction: String,
    val lastTarget: String?,
    val confidence: ContextConfidence,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isExpired() = System.currentTimeMillis() - timestamp > CONTEXT_EXPIRY_ACTION_MS
}

data class QueryContext(
    val query: String,
    val queryType: String, // "search" | "navigation"
    val confidence: ContextConfidence,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isExpired() = System.currentTimeMillis() - timestamp > CONTEXT_EXPIRY_QUERY_MS
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
}
