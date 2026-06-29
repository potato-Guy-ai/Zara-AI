package com.zara.assistant.ui

import android.app.Activity
import android.os.Bundle
import com.zara.assistant.playback.SpotifyAuthManager
import com.zara.assistant.utils.ZaraLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Layer 6.6C.2 — SpotifyAuthActivity.
 * Receives the Spotify PKCE redirect: com.zara.assistant://spotify-auth
 * Performs the token exchange (network call) off the main thread,
 * then finishes immediately — no UI shown.
 */
class SpotifyAuthActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null) {
            ZaraLogger.e("[SpotifyAuthActivity] launched with no redirect data")
            finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val success = SpotifyAuthManager.handleRedirect(uri)
            ZaraLogger.d("[SpotifyAuthActivity] token exchange success=$success")
            finish()
        }
    }
}
