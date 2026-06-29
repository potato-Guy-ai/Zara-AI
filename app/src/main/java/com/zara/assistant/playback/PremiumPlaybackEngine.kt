package com.zara.assistant.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Layer 6.6C.2 — PremiumPlaybackEngine.
 *
 * PREMIUM_DIRECT route execution. As of this layer, uses real
 * SpotifyApiClientImpl by default instead of the mock — real PKCE auth,
 * real Web API search, real App Remote playback.
 *
 * run() is now suspend + withContext(Dispatchers.IO) because the
 * underlying client performs blocking network calls (OkHttp .execute(),
 * App Remote connect via CountDownLatch). This mirrors ActionExecutor's
 * existing suspend-based execution pattern elsewhere in the codebase —
 * not a new/unnecessary coroutine usage, but the minimum needed to keep
 * real network I/O off the caller's thread.
 *
 * No threads spun up directly here. No services. No polling. No timers.
 */
class PremiumPlaybackEngine(
    private val client: SpotifyApiClient
) {

    /**
     * Runs the direct-playback flow for a PREMIUM_DIRECT plan.
     * Ignores any plan whose route is not PREMIUM_DIRECT.
     */
    suspend fun run(plan: PlaybackExecutionPlan): SpotifyPlaybackResult = withContext(Dispatchers.IO) {
        if (plan.route != PlaybackRoute.PREMIUM_DIRECT) {
            return@withContext SpotifyPlaybackResult(
                type = SpotifyPlaybackResultType.FAILED,
                state = SpotifyPlaybackState.IDLE,
                message = "Not a PREMIUM_DIRECT plan."
            )
        }

        val authState = client.getAuthState()
        if (authState != SpotifyAuthState.AUTHENTICATED) {
            return@withContext SpotifyPlaybackResult(
                type = SpotifyPlaybackResultType.AUTH_REQUIRED,
                state = SpotifyPlaybackState.AUTH_REQUIRED,
                message = "Please connect Spotify first."
            )
        }

        val result = search(plan)
        if (result == null) {
            return@withContext SpotifyPlaybackResult(
                type = SpotifyPlaybackResultType.FAILED,
                state = SpotifyPlaybackState.FAILED,
                message = "Couldn\u2019t find that track."
            )
        }

        val played = client.play(result.uri)
        if (played) {
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
            PlaybackExecutionType.PLAY_LIKED ->
                (client as? SpotifyApiClientImpl)?.likedSongs() ?: client.getRecommendations(query)
            PlaybackExecutionType.SEARCH_ONLY -> null
        }
    }
}
