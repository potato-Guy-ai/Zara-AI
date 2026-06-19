package com.zara.assistant.playback

/**
 * Layer 6.6A Phase 1 — UserTierDetector.
 *
 * Architecture foundation only. No OAuth. No Spotify API calls.
 * Placeholder state used until real account-linking is implemented.
 */
object UserTierDetector {

    // Placeholder in-memory state. Future phases will replace this
    // with real Spotify account linking / token state.
    private var placeholderTier: UserTier = UserTier.NOT_CONNECTED

    fun detect(): UserTier = placeholderTier

    /** Test/placeholder hook only — not wired to any real auth flow. */
    fun setPlaceholderTier(tier: UserTier) {
        placeholderTier = tier
    }
}
