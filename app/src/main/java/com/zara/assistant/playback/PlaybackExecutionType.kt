package com.zara.assistant.playback

/**
 * Layer 6.6B Phase 2A — PlaybackExecutionType.
 * Describes what kind of playback action the plan targets.
 * No execution logic here.
 */
enum class PlaybackExecutionType {
    PLAY_SONG,
    PLAY_PLAYLIST,
    PLAY_LIKED,
    PLAY_RECOMMENDATION,
    SEARCH_ONLY
}
