package com.zara.assistant.playback

/**
 * Layer 6.6B Phase 2C.1 — PremiumPlaybackEngine.
 *
 * Architecture foundation for PREMIUM_DIRECT route. Uses a mocked
 * SpotifyApiClient — no network, no OAuth, no real playback.
 *
 * Phase 2C.2 plug-in point: construct this engine with a real
 * SpotifyApiClient implementation instead of MockSpotifyApiClient.
 * No other change required in this file's call sites.
 *
 * No threads. No services. No polling. No timers. No coroutines.
 */
class PremiumPlaybackEngine(
    private val client: SpotifyApiClient = MockSpotifyApiClient
) {

    /**
     * Runs the direct-playback flow for a PREMIUM_DIRECT plan.
     * Ignores any plan whose route is not PREMIUM_DIRECT.
     */
    fun run(plan: PlaybackExecutionPlan): SpotifyPlaybackResult {
        if (plan.route != PlaybackRoute.PREMIUM_DIRECT) {
            return SpotifyPlaybackResult(
                type = SpotifyPlaybackResultType.FAILED,
                state = SpotifyPlaybackState.IDLE,
                message = "Not a PREMIUM_DIRECT plan."
            )
        }

        // Step 1: validate auth state before any search/playback attempt.
        val authState = client.getAuthState()
        if (authState != SpotifyAuthState.AUTHENTICATED) {
            return SpotifyPlaybackResult(
                type = SpotifyPlaybackResultType.AUTH_REQUIRED,
                state = SpotifyPlaybackState.AUTH_REQUIRED,
                message = "Spotify authentication required."
            )
        }

        // Step 2: search target based on execution type.
        val result = search(plan)
        if (result == null) {
            return SpotifyPlaybackResult(
                type = SpotifyPlaybackResultType.FAILED,
                state = SpotifyPlaybackState.FAILED,
                message = "No match found for \"${plan.resolvedQuery}\"."
            )
        }

        // Step 3: trigger playback (mocked).
        val played = client.play(result.uri)
        return if (played) {
            SpotifyPlaybackResult(
                type = SpotifyPlaybackResultType.SUCCESS,
                state = SpotifyPlaybackState.PLAYING,
                message = "Playing ${result.name}."
            )
        } else {
            SpotifyPlaybackResult(
                type = SpotifyPlaybackResultType.FAILED,
                state = SpotifyPlaybackState.FAILED,
                message = "Playback failed for ${result.name}."
            )
        }
    }

    private fun search(plan: PlaybackExecutionPlan): SpotifySearchResult? {
        val query = plan.resolvedQuery
        return when (plan.executionType) {
            PlaybackExecutionType.PLAY_SONG -> client.searchTrack(query)
            PlaybackExecutionType.PLAY_PLAYLIST -> client.searchPlaylist(query)
            PlaybackExecutionType.PLAY_RECOMMENDATION -> client.getRecommendations(query)
            PlaybackExecutionType.PLAY_LIKED -> client.getRecommendations(query)
            PlaybackExecutionType.SEARCH_ONLY -> null
        }
    }
}
