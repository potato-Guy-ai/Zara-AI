package com.zara.assistant.playback

import com.google.gson.Gson
import com.zara.assistant.utils.ZaraLogger
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Layer 6.6A Phase 1 — UserTierDetector.
 *
 * Layer 6.6 fix: real FREE tier detection.
 * Previous behavior treated any authenticated user as PREMIUM — logged-in
 * FREE users were incorrectly routed to PremiumPlaybackEngine. Now calls
 * Spotify Web API GET /me and reads the documented `product` field
 * ("premium" / "free" / "open") to distinguish the two.
 *
 *   not authenticated         -> NOT_CONNECTED
 *   authenticated, product == "premium" -> PREMIUM
 *   authenticated, anything else (incl. lookup failure) -> FREE
 *
 * Failing safe to FREE (rather than PREMIUM) on a lookup error keeps a
 * non-premium account from ever being routed into the premium-only path.
 *
 * NOTE (honesty flag): this /me call is written to Spotify's documented
 * Web API contract but has not been executed against the live endpoint in
 * this environment — no network/Android runtime available here. Treat as
 * unverified-by-execution until run on-device.
 */
object UserTierDetector {

    private const val ME_ENDPOINT = "https://api.spotify.com/v1/me"

    private val httpClient = OkHttpClient()
    private val gson = Gson()

    fun detect(): UserTier {
        if (!SpotifyAuthManager.isAuthenticated()) return UserTier.NOT_CONNECTED
        val token = SpotifyAuthManager.currentAccessToken() ?: return UserTier.NOT_CONNECTED
        val product = fetchProduct(token)
        return if (product.equals("premium", ignoreCase = true)) UserTier.PREMIUM else UserTier.FREE
    }

    private fun fetchProduct(token: String): String? {
        return try {
            val request = Request.Builder()
                .url(ME_ENDPOINT)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string()
                if (!response.isSuccessful || raw.isNullOrBlank()) {
                    ZaraLogger.e("[UserTierDetector] /me failed: HTTP ${response.code}")
                    return null
                }
                gson.fromJson(raw, SpotifyUserProfileResponse::class.java)?.product
            }
        } catch (e: Exception) {
            ZaraLogger.e("[UserTierDetector] /me exception: ${e.message}")
            null
        }
    }
}
