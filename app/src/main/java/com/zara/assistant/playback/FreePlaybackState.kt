package com.zara.assistant.playback

/**
 * Layer 6.6B Phase 2B — FreePlaybackState.
 * State machine for the assisted-launch flow. No execution semantics
 * beyond app-open + deeplink injection.
 */
enum class FreePlaybackState {
    IDLE,
    OPENING_APP,
    SEARCHING,
    READY,
    FAILED
}
