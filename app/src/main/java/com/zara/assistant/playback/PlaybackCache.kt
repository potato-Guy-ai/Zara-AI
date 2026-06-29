package com.zara.assistant.playback

/**
 * Layer 6.6A Phase 1 — PlaybackCache.
 *
 * In-memory ring buffer. No persistence. No Room. No DataStore.
 * Max 20 entries. TTL 10 minutes.
 * Session-only state, cleared on process death.
 */
object PlaybackCache {

    data class Entry(
        val query: String,
        val target: PlaybackTarget,
        val timestamp: Long
    )

    private const val MAX_ENTRIES = 20
    private const val TTL_MS = 10 * 60 * 1000L

    private val buffer = ArrayDeque<Entry>()

    fun put(query: String, target: PlaybackTarget) {
        evictExpired()
        if (buffer.size >= MAX_ENTRIES) buffer.removeFirst()
        buffer.addLast(Entry(query, target, System.currentTimeMillis()))
    }

    fun get(query: String): PlaybackTarget? {
        evictExpired()
        return buffer.lastOrNull { it.query == query }?.target
    }

    fun clear() = buffer.clear()

    fun size(): Int = buffer.size

    private fun evictExpired() {
        val now = System.currentTimeMillis()
        while (buffer.isNotEmpty() && now - buffer.first().timestamp > TTL_MS) {
            buffer.removeFirst()
        }
    }
}
