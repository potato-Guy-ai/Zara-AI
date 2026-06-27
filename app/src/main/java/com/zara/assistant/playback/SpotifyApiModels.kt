package com.zara.assistant.playback

import com.google.gson.annotations.SerializedName

/**
 * Layer 6.6C.2 — SpotifyApiModels.
 *
 * Gson-deserializable shapes for Spotify Web API JSON responses.
 * Only fields actually used by SpotifyApiClientImpl are declared —
 * Gson ignores unknown JSON fields by default, so this is safe even
 * though Spotify's real responses contain many more fields.
 */

// --- Token exchange response (https://accounts.spotify.com/api/token) ---
data class SpotifyTokenResponse(
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("token_type") val tokenType: String?,
    @SerializedName("expires_in") val expiresIn: Long?,
    @SerializedName("refresh_token") val refreshToken: String?,
    @SerializedName("scope") val scope: String?,
    @SerializedName("error") val error: String?,
    @SerializedName("error_description") val errorDescription: String?
)

// --- Search response (https://api.spotify.com/v1/search) ---
data class SpotifySearchResponse(
    @SerializedName("tracks") val tracks: SpotifyPagedTracks?,
    @SerializedName("playlists") val playlists: SpotifyPagedPlaylists?,
    @SerializedName("artists") val artists: SpotifyPagedArtists?
)

data class SpotifyPagedTracks(@SerializedName("items") val items: List<SpotifyTrack>?)
data class SpotifyPagedPlaylists(@SerializedName("items") val items: List<SpotifyPlaylist>?)
data class SpotifyPagedArtists(@SerializedName("items") val items: List<SpotifyArtist>?)

data class SpotifyTrack(
    @SerializedName("uri") val uri: String?,
    @SerializedName("name") val name: String?
)

data class SpotifyPlaylist(
    @SerializedName("uri") val uri: String?,
    @SerializedName("name") val name: String?
)

data class SpotifyArtist(
    @SerializedName("uri") val uri: String?,
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?
)

// --- Artist top tracks (https://api.spotify.com/v1/artists/{id}/top-tracks) ---
data class SpotifyTopTracksResponse(
    @SerializedName("tracks") val tracks: List<SpotifyTrack>?
)

// --- Recommendations (https://api.spotify.com/v1/recommendations) ---
data class SpotifyRecommendationsResponse(
    @SerializedName("tracks") val tracks: List<SpotifyTrack>?
)

// --- Current user's top tracks (https://api.spotify.com/v1/me/top/tracks) ---
data class SpotifyUserTopTracksResponse(
    @SerializedName("items") val items: List<SpotifyTrack>?
)

// --- Saved (liked) tracks (https://api.spotify.com/v1/me/tracks) ---
data class SpotifySavedTracksResponse(
    @SerializedName("items") val items: List<SpotifySavedTrackItem>?
)

data class SpotifySavedTrackItem(
    @SerializedName("track") val track: SpotifyTrack?
)

// --- Current user's profile (https://api.spotify.com/v1/me) ---
// Layer 6.6 fix: used by UserTierDetector to distinguish FREE vs PREMIUM.
// `product` is Spotify's documented field: "premium", "free", or "open".
data class SpotifyUserProfileResponse(
    @SerializedName("product") val product: String?
)
