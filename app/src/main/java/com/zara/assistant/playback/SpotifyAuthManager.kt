package com.zara.assistant.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.gson.Gson
import com.zara.assistant.utils.ZaraLogger
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Layer 6.6C.2 — SpotifyAuthManager.
 *
 * Orchestrates Spotify's Authorization Code with PKCE flow.
 * No client secret stored or transmitted anywhere — PKCE explicitly
 * removes that requirement for public/native clients.
 *
 * NOTE (honesty flag): this code is written to Spotify's documented
 * PKCE + Web API contract but has not been executed against the live
 * Spotify endpoints in this environment — there is no network path or
 * Android runtime available here to verify it end-to-end. Treat as
 * unverified-by-execution until tested on-device.
 */
object SpotifyAuthManager {

    private const val CLIENT_ID = "db837c3549e64964901a333c0f00a781"
    private const val REDIRECT_URI = "com.zara.assistant://spotify-auth"
    private const val AUTH_ENDPOINT = "https://accounts.spotify.com/authorize"
    private const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"

    private val SCOPES = listOf(
        "user-read-private",
        "user-read-email",
        "streaming",
        "user-modify-playback-state",
        "user-read-playback-state"
    ).joinToString(" ")

    private val client = OkHttpClient()
    private val gson = Gson()

    /**
     * Step 1: build PKCE pair, store the verifier, launch the system
     * browser to Spotify's auth page. The redirect lands in
     * SpotifyAuthActivity, which calls handleRedirect() below.
     */
    fun beginAuth(context: Context) {
        val pkce = SpotifyPkceManager.generate()
        SpotifyTokenStore.setPendingCodeVerifier(pkce.codeVerifier)

        val authUri = Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", pkce.codeChallenge)
            .appendQueryParameter("scope", SCOPES)
            .build()

        try {
            val intent = Intent(Intent.ACTION_VIEW, authUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            ZaraLogger.e("[SpotifyAuthManager] failed to launch auth browser: ${e.message}")
        }
    }

    /**
     * Step 2: called from SpotifyAuthActivity with the redirect Uri.
     * Extracts the `code` param, exchanges it for tokens.
     * Returns true on success, false on any failure. Never throws.
     */
    fun handleRedirect(uri: Uri): Boolean {
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")
        if (error != null) {
            ZaraLogger.e("[SpotifyAuthManager] auth redirect error: $error")
            SpotifyTokenStore.consumePendingCodeVerifier()
            return false
        }
        if (code.isNullOrBlank()) {
            ZaraLogger.e("[SpotifyAuthManager] redirect missing code param")
            return false
        }

        val verifier = SpotifyTokenStore.consumePendingCodeVerifier()
        if (verifier == null) {
            ZaraLogger.e("[SpotifyAuthManager] no pending code_verifier for redirect")
            return false
        }

        return exchangeCodeForToken(code, verifier)
    }

    /** Blocking network call — caller must run off the main thread. */
    private fun exchangeCodeForToken(code: String, verifier: String): Boolean {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("code_verifier", verifier)
            .build()

        return runTokenRequest(body)
    }

    /**
     * Refreshes the access token using the stored refresh token.
     * Blocking network call — caller must run off the main thread.
     * Returns true on success, false on failure (token left unchanged
     * if refresh fails so a stale-but-present token isn't silently lost
     * without explanation — caller treats failure as AUTH_REQUIRED).
     */
    fun refreshTokenIfNeeded(): Boolean {
        val current = SpotifyTokenStore.get() ?: return false
        if (!SpotifyTokenStore.isExpired()) return true
        val refreshToken = current.refreshToken ?: return false

        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .build()

        return runTokenRequest(body)
    }

    private fun runTokenRequest(body: FormBody): Boolean {
        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string()
                if (!response.isSuccessful || raw.isNullOrBlank()) {
                    ZaraLogger.e("[SpotifyAuthManager] token request failed: HTTP ${response.code}")
                    return false
                }
                val parsed = gson.fromJson(raw, SpotifyTokenResponse::class.java)
                if (parsed?.error != null || parsed?.accessToken == null) {
                    ZaraLogger.e("[SpotifyAuthManager] token error: ${parsed?.error} ${parsed?.errorDescription}")
                    return false
                }
                SpotifyTokenStore.save(
                    accessToken = parsed.accessToken,
                    refreshToken = parsed.refreshToken ?: SpotifyTokenStore.get()?.refreshToken,
                    expiresInSeconds = parsed.expiresIn ?: 3600L
                )
                true
            }
        } catch (e: Exception) {
            ZaraLogger.e("[SpotifyAuthManager] token request exception: ${e.message}")
            false
        }
    }

    fun isAuthenticated(): Boolean = SpotifyTokenStore.hasToken() && !SpotifyTokenStore.isExpired()

    fun currentAccessToken(): String? = SpotifyTokenStore.get()?.accessToken
}
