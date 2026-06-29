package com.zara.assistant.playback

/**
 * Layer 6.6C.2 — SpotifyTokenStore.
 *
 * In-memory only. No persistence (no DataStore, no Room, no SharedPrefs).
 * Cleared on process death, by design (spec: "no persistence yet").
 */
object SpotifyTokenStore {

    data class TokenData(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtMillis: Long
    )

    @Volatile
    private var token: TokenData? = null

    // PKCE code_verifier held only for the duration of one auth round-trip
    // (generated before opening the browser, consumed at token exchange).
    @Volatile
    private var pendingCodeVerifier: String? = null

    fun setPendingCodeVerifier(verifier: String) {
        pendingCodeVerifier = verifier
    }

    fun consumePendingCodeVerifier(): String? {
        val v = pendingCodeVerifier
        pendingCodeVerifier = null
        return v
    }

    fun save(accessToken: String, refreshToken: String?, expiresInSeconds: Long) {
        token = TokenData(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        )
    }

    fun get(): TokenData? = token

    fun clear() {
        token = null
        pendingCodeVerifier = null
    }

    fun isExpired(): Boolean {
        val t = token ?: return true
        // 30s safety margin before real expiry.
        return System.currentTimeMillis() >= (t.expiresAtMillis - 30_000L)
    }

    fun hasToken(): Boolean = token != null
}
