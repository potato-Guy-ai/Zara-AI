package com.zara.assistant.playback

/**
 * Layer 6.6B Phase 2C.1 — SpotifySearchResult.
 * Generic shape returned by all SpotifyApiClient search methods.
 * Architecture only — fields chosen to be HTTP-response-shaped so
 * Phase 2C.2 can populate them from a real Spotify API JSON body
 * without changing this model.
 */
data class SpotifySearchResult(
    val uri: String,
    val name: String,
    val type: PlaybackType,
    val confidence: Float
)
