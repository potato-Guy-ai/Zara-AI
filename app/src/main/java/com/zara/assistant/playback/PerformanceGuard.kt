package com.zara.assistant.playback

/**
 * Layer 6.6A Phase 1 — PerformanceGuard.
 *
 * Protects low-end devices. Metadata-only guard rules.
 * No timers. No threads. No services. No heavy loops.
 */
object PerformanceGuard {

    const val MAX_RESULTS = 3
    const val TIMEOUT_HINT_MS = 1500L // metadata only — not enforced via timer/thread here

    /** Truncates a result list to MAX_RESULTS without extra allocation overhead. */
    fun <T> limit(results: List<T>): List<T> {
        if (results.size <= MAX_RESULTS) return results
        return results.subList(0, MAX_RESULTS)
    }

    /** Early-stop helper for O(n) scans — call inside loops, no threading involved. */
    fun shouldStop(processedCount: Int): Boolean = processedCount >= MAX_RESULTS
}
