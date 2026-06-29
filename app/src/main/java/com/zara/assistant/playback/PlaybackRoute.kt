package com.zara.assistant.playback

/**
 * Layer 6.6B Phase 2A — PlaybackRoute.
 * Describes how a plan would eventually be executed (future phase).
 * No execution logic here.
 */
enum class PlaybackRoute {
    PREMIUM_DIRECT,
    FREE_ASSISTED,
    FALLBACK_SEARCH
}
