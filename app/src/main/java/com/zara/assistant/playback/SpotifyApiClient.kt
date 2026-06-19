package com.zara.assistant.playback

/**
 * Layer 6.6B Phase 2C.1 — SpotifyApiClient.
 *
 * Abstraction boundary for all Spotify Web API interaction.
 * This phase ships ONLY a mocked implementation: no HTTP, no OAuth,
 * no network, no real credentials.
 *
 * Phase 2C.2 plug-in point: implement this same interface with real
 * HTTP calls (e.g. `SpotifyApiClientImpl` using OkHttp/Retrofit + OAuth
 * token storage) and swap it into PremiumPlaybackEngine's constructor.
 * No other file needs to change.
 */
interface SpotifyApiClient {
    fun getAuthState(): SpotifyAuthState
    fun searchTrack(query: String): SpotifySearchResult?
    fun searchPlaylist(query: String): SpotifySearchResult?
    fun searchArtist(query: String): SpotifySearchResult?
    fun getRecommendations(query: String): SpotifySearchResult?
    fun play(uri: String): Boolean
}

/**
 * Mocked implementation. Deterministic, in-memory only.
 * No network. No HTTP. No OAuth. Used until Phase 2C.2.
 */
object MockSpotifyApiClient : SpotifyApiClient {

    // Placeholder auth state — mirrors UserTierDetector's placeholder
    // pattern from Layer 6.6A. Future phase replaces with real token state.
    private var mockAuthState: SpotifyAuthState = SpotifyAuthState.AUTHENTICATED

    /** Test/placeholder hook only — not wired to any real auth flow. */
    fun setMockAuthState(state: SpotifyAuthState) {
        mockAuthState = state
    }

    override fun getAuthState(): SpotifyAuthState = mockAuthState

    override fun searchTrack(query: String): SpotifySearchResult? {
        if (query.isBlank()) return null
        return SpotifySearchResult(
            uri = "spotify:track:mock:${normalize(query)}",
            name = query,
            type = PlaybackType.SONG,
            confidence = 1.0f
        )
    }

    override fun searchPlaylist(query: String): SpotifySearchResult? {
        if (query.isBlank()) return null
        return SpotifySearchResult(
            uri = "spotify:playlist:mock:${normalize(query)}",
            name = query,
            type = PlaybackType.PLAYLIST,
            confidence = 1.0f
        )
    }

    override fun searchArtist(query: String): SpotifySearchResult? {
        if (query.isBlank()) return null
        return SpotifySearchResult(
            uri = "spotify:artist:mock:${normalize(query)}",
            name = query,
            type = PlaybackType.ARTIST,
            confidence = 1.0f
        )
    }

    override fun getRecommendations(query: String): SpotifySearchResult? {
        return SpotifySearchResult(
            uri = "spotify:recommendation:mock:${normalize(query)}",
            name = query.ifBlank { "your top tracks" },
            type = PlaybackType.RECOMMENDATION,
            confidence = 0.8f
        )
    }

    override fun play(uri: String): Boolean {
        // Mocked playback trigger — always succeeds for a non-blank uri.
        return uri.isNotBlank()
    }

    private fun normalize(query: String): String =
        query.trim().lowercase().replace(" ", "_")
}
