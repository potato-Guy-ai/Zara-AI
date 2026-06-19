package com.zara.assistant.playback

/**
 * Layer 6.6B Phase 2B — FreePlaybackResult.
 * Outcome of FreePlaybackEngine.run(). Immutable.
 */
enum class FreePlaybackResultType {
    SUCCESS,
    FAILED,
    FALLBACK_USED
}

data class FreePlaybackResult(
    val type: FreePlaybackResultType,
    val state: FreePlaybackState,
    val message: String
)
