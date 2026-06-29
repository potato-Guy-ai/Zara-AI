package com.zara.assistant.playback

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Layer 6.6C.2 — SpotifyPkceManager.
 *
 * Generates PKCE code_verifier / code_challenge pairs per RFC 7636,
 * as required by Spotify's Authorization Code with PKCE flow.
 * No client secret used or stored anywhere.
 *
 * code_verifier: 43–128 char URL-safe random string.
 * code_challenge: BASE64URL(SHA256(code_verifier)), no padding.
 */
object SpotifyPkceManager {

    private const val VERIFIER_BYTE_LENGTH = 64 // -> ~86 base64url chars, within 43-128 range

    data class PkcePair(val codeVerifier: String, val codeChallenge: String)

    fun generate(): PkcePair {
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        return PkcePair(codeVerifier = verifier, codeChallenge = challenge)
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(VERIFIER_BYTE_LENGTH)
        SecureRandom().nextBytes(bytes)
        return base64UrlEncode(bytes)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return base64UrlEncode(digest)
    }

    private fun base64UrlEncode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
