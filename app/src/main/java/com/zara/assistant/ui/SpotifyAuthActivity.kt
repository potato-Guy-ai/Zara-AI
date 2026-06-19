package com.zara.assistant.ui

import android.app.Activity
import android.os.Bundle

/**
 * Layer 6.6 Phase 2C.2 — infra shell only.
 * Receives the Spotify PKCE redirect: com.zara.assistant://spotify-auth
 * No logic implemented yet — token exchange comes in a later step.
 */
class SpotifyAuthActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No-op shell. Redirect handling logic added in a future step.
        finish()
    }
}
