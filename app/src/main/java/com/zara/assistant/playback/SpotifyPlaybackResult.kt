package com.zara.assistant.playback

/**
 * Layer 6.6B Phase 2C.1 — SpotifyPlaybackResult.
 * Outcome of PremiumPlaybackEngine.run(). Immutable.
 */
enum class SpotifyPlaybackResultType {
    SUCCESS,
    FAILED,
    AUTH_REQUIRED
}

data class SpotifyPlaybackResult(
    val type: SpotifyPlaybackResultType,
    val state: SpotifyPlaybackState,
    val message: String
)
