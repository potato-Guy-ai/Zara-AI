package com.zara.assistant.playback

/**
 * Layer 6.6B Phase 2C.1 — SpotifyPlaybackState.
 * State machine for PremiumPlaybackEngine. Architecture only.
 */
enum class SpotifyPlaybackState {
    IDLE,
    AUTH_REQUIRED,
    SEARCHING,
    PLAYING,
    FAILED
}
