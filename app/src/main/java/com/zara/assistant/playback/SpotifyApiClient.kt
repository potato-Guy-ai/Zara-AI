package com.zara.assistant.playback

import android.content.Context
import com.google.gson.Gson
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.zara.assistant.utils.ZaraLogger
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Layer 6.6C.2 — SpotifyApiClient.
 *
 * Abstraction boundary for all Spotify Web API + App Remote interaction.
 *
 * NOTE (honesty flag): SpotifyApiClientImpl below is written against
 * Spotify's documented Web API contract and App Remote SDK surface
 * (SpotifyAppRemote.connect/disconnect, PlayerApi.play). It has not
 * been executed in this environment — there is no Android runtime,
 * emulator, or network path to api.spotify.com / the App Remote
 * service here. Treat as unverified-by-execution until run on-device.
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
 * Mocked implementation. Kept for tests / Phase 2A-2C.1 callers that
 * have not migrated. Not used by PremiumPlaybackEngine's default path
 * anymore as of Layer 6.6C.2 — superseded by SpotifyApiClientImpl.
 */
object MockSpotifyApiClient : SpotifyApiClient {

    private var mockAuthState: SpotifyAuthState = SpotifyAuthState.AUTHENTICATED

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
        return uri.isNotBlank()
    }

    private fun normalize(query: String): String =
        query.trim().lowercase().replace(" ", "_")
}

/**
 * Real implementation. Web API for search/metadata resolution only.
 * App Remote SDK for actual playback (per spec: playback never goes
 * through the Web API).
 *
 * No persistent state beyond what SpotifyTokenStore/SpotifyAuthManager
 * already hold. No polling, no services, no timers. App Remote connect
 * is a one-shot blocking-with-timeout call per play() invocation —
 * simplest correct approach given "no unnecessary coroutines"; a
 * persistent long-lived connection would need a Service/lifecycle owner,
 * which is out of scope for this phase.
 */
class SpotifyApiClientImpl(private val context: Context) : SpotifyApiClient {

    companion object {
        private const val CLIENT_ID = "db837c3549e64964901a333c0f00a781"
        private const val REDIRECT_URI = "com.zara.assistant://spotify-auth"
        private const val WEB_API_BASE = "https://api.spotify.com/v1"
        private const val REMOTE_CONNECT_TIMEOUT_SECONDS = 8L
    }

    private val httpClient = OkHttpClient()
    private val gson = Gson()

    override fun getAuthState(): SpotifyAuthState {
        if (!SpotifyTokenStore.hasToken()) return SpotifyAuthState.NOT_AUTHENTICATED
        if (SpotifyTokenStore.isExpired()) {
            val refreshed = SpotifyAuthManager.refreshTokenIfNeeded()
            return if (refreshed) SpotifyAuthState.AUTHENTICATED else SpotifyAuthState.TOKEN_EXPIRED
        }
        return SpotifyAuthState.AUTHENTICATED
    }

    override fun searchTrack(query: String): SpotifySearchResult? {
        if (query.isBlank()) return null
        val response = doSearch(query, "track") ?: return null
        val track = response.tracks?.items?.firstOrNull() ?: return null
        val uri = track.uri ?: return null
        return SpotifySearchResult(uri = uri, name = track.name ?: query, type = PlaybackType.SONG, confidence = 1.0f)
    }

    override fun searchPlaylist(query: String): SpotifySearchResult? {
        if (query.isBlank()) return null
        val response = doSearch(query, "playlist") ?: return null
        val playlist = response.playlists?.items?.firstOrNull() ?: return null
        val uri = playlist.uri ?: return null
        return SpotifySearchResult(uri = uri, name = playlist.name ?: query, type = PlaybackType.PLAYLIST, confidence = 1.0f)
    }

    /**
     * Spec: "ARTIST → searchArtist(), use artist top tracks."
     * An artist URI isn't directly playable as a single track via
     * App Remote the way a search result normally is, so this resolves
     * the artist, then fetches their top track and returns THAT uri —
     * matching the spec's stated behavior rather than the raw artist URI.
     */
    override fun searchArtist(query: String): SpotifySearchResult? {
        if (query.isBlank()) return null
        val response = doSearch(query, "artist") ?: return null
        val artist = response.artists?.items?.firstOrNull() ?: return null
        val artistId = artist.id ?: return null
        val topTrack = fetchArtistTopTrack(artistId) ?: return null
        return SpotifySearchResult(
            uri = topTrack.uri ?: return null,
            name = topTrack.name ?: artist.name ?: query,
            type = PlaybackType.ARTIST,
            confidence = 0.9f
        )
    }

    override fun getRecommendations(query: String): SpotifySearchResult? {
        val token = SpotifyTokenStore.get()?.accessToken ?: return null

        val recTrack = fetchRecommendation(query, token)
        if (recTrack != null) {
            return SpotifySearchResult(
                uri = recTrack.uri ?: return null,
                name = recTrack.name ?: query.ifBlank { "a recommended track" },
                type = PlaybackType.RECOMMENDATION,
                confidence = 0.7f
            )
        }

        val topTrack = fetchUserTopTrack(token) ?: return fetchLikedTrack(token)
        return SpotifySearchResult(
            uri = topTrack.uri ?: return null,
            name = topTrack.name ?: "your top tracks",
            type = PlaybackType.RECOMMENDATION,
            confidence = 0.6f
        )
    }

    /**
     * Playback always goes through App Remote, never the Web API.
     */
    override fun play(uri: String): Boolean {
        if (uri.isBlank()) return false

        val params = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(false)
            .build()

        val latch = CountDownLatch(1)
        var playbackSucceeded = false
        var remoteRef: SpotifyAppRemote? = null

        SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                remoteRef = appRemote
                appRemote.playerApi.play(uri)
                    .setResultCallback {
                        playbackSucceeded = true
                        latch.countDown()
                    }
                    .setErrorCallback { error ->
                        ZaraLogger.e("[SpotifyApiClientImpl] play() error: ${error.message}")
                        playbackSucceeded = false
                        latch.countDown()
                    }
            }

            override fun onFailure(throwable: Throwable) {
                ZaraLogger.e("[SpotifyApiClientImpl] App Remote connect failed: ${throwable.message}")
                playbackSucceeded = false
                latch.countDown()
            }
        })

        val completed = try {
            latch.await(REMOTE_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            false
        }

        remoteRef?.let { SpotifyAppRemote.disconnect(it) }

        if (!completed) {
            ZaraLogger.e("[SpotifyApiClientImpl] App Remote play() timed out")
            return false
        }
        return playbackSucceeded
    }

    private fun doSearch(query: String, type: String): SpotifySearchResponse? {
        val token = SpotifyTokenStore.get()?.accessToken ?: return null
        return try {
            val request = Request.Builder()
                .url(
                    "$WEB_API_BASE/search".toHttpUrlOrNullSafe()
                        ?.newBuilder()
                        ?.addQueryParameter("q", query)
                        ?.addQueryParameter("type", type)
                        ?.addQueryParameter("limit", "1")
                        ?.build()
                        ?: return null
                )
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            executeJson(request, SpotifySearchResponse::class.java)
        } catch (e: Exception) {
            ZaraLogger.e("[SpotifyApiClientImpl] search failed: ${e.message}")
            null
        }
    }

    private fun fetchArtistTopTrack(artistId: String): SpotifyTrack? {
        val token = SpotifyTokenStore.get()?.accessToken ?: return null
        return try {
            val request = Request.Builder()
                .url("$WEB_API_BASE/artists/$artistId/top-tracks?market=from_token")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            executeJson(request, SpotifyTopTracksResponse::class.java)?.tracks?.firstOrNull()
        } catch (e: Exception) {
            ZaraLogger.e("[SpotifyApiClientImpl] top-tracks failed: ${e.message}")
            null
        }
    }

    private fun fetchRecommendation(seedGenreGuess: String, token: String): SpotifyTrack? {
        if (seedGenreGuess.isBlank()) return null
        return try {
            val request = Request.Builder()
                .url(
                    "$WEB_API_BASE/recommendations".toHttpUrlOrNullSafe()
                        ?.newBuilder()
                        ?.addQueryParameter("seed_genres", seedGenreGuess.trim().lowercase().replace(" ", "-"))
                        ?.addQueryParameter("limit", "1")
                        ?.build()
                        ?: return null
                )
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            executeJson(request, SpotifyRecommendationsResponse::class.java)?.tracks?.firstOrNull()
        } catch (e: Exception) {
            ZaraLogger.e("[SpotifyApiClientImpl] recommendations failed: ${e.message}")
            null
        }
    }

    private fun fetchUserTopTrack(token: String): SpotifyTrack? {
        return try {
            val request = Request.Builder()
                .url("$WEB_API_BASE/me/top/tracks?limit=1")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            executeJson(request, SpotifyUserTopTracksResponse::class.java)?.items?.firstOrNull()
        } catch (e: Exception) {
            ZaraLogger.e("[SpotifyApiClientImpl] user top tracks failed: ${e.message}")
            null
        }
    }

    /** "liked songs" / "my likes" / "saved songs" fallback path. */
    private fun fetchLikedTrack(token: String): SpotifySearchResult? {
        return try {
            val request = Request.Builder()
                .url("$WEB_API_BASE/me/tracks?limit=1")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            val track = executeJson(request, SpotifySavedTracksResponse::class.java)
                ?.items?.firstOrNull()?.track ?: return null
            val uri = track.uri ?: return null
            SpotifySearchResult(uri = uri, name = track.name ?: "a liked song", type = PlaybackType.LIKED, confidence = 0.7f)
        } catch (e: Exception) {
            ZaraLogger.e("[SpotifyApiClientImpl] liked tracks failed: ${e.message}")
            null
        }
    }

    fun likedSongs(): SpotifySearchResult? {
        val token = SpotifyTokenStore.get()?.accessToken ?: return null
        return fetchLikedTrack(token)
    }

    private fun <T> executeJson(request: Request, classOfT: Class<T>): T? {
        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string()
            if (!response.isSuccessful || raw.isNullOrBlank()) {
                ZaraLogger.e("[SpotifyApiClientImpl] HTTP ${response.code} for ${request.url}")
                return null
            }
            return gson.fromJson(raw, classOfT)
        }
    }

    private fun String.toHttpUrlOrNullSafe(): okhttp3.HttpUrl? = this.toHttpUrlOrNull()
}
