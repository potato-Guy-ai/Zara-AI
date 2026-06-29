package com.zara.assistant.playback

/**
 * Layer 6.6B Phase 2C.1 — SpotifyAuthState.
 * Architecture only. No real OAuth wired yet — populated by mocked
 * SpotifyApiClient until Phase 2C.2 replaces it with real token state.
 */
enum class SpotifyAuthState {
    NOT_AUTHENTICATED,
    TOKEN_EXPIRED,
    AUTHENTICATED
}
