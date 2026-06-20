package com.zara.assistant.playback

/**
 * Layer 6.6A Phase 1 — UserTierDetector.
 *
 * Derives tier from real Spotify auth state (SpotifyAuthManager /
 * SpotifyTokenStore). No FREE detection yet — anything authenticated
 * is treated as PREMIUM; anything else is NOT_CONNECTED.
 */
object UserTierDetector {

    fun detect(): UserTier {
        return if (SpotifyAuthManager.isAuthenticated()) UserTier.PREMIUM else UserTier.NOT_CONNECTED
    }
}
