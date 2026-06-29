package com.zara.assistant.playback

/**
 * Layer 6.6A — Smart Playback Foundation — Phase 1.
 *
 * Architecture-only models. No execution logic. No API calls.
 */

enum class PlaybackType {
    SONG,
    PLAYLIST,
    ALBUM,
    ARTIST,
    LIKED,
    RECOMMENDATION,
    UNKNOWN
}

enum class PlaybackStrategy {
    API_MODE,
    DEEPLINK_MODE,
    LOCAL_ASSIST_MODE
}

enum class UserTier {
    NOT_CONNECTED,
    FREE,
    PREMIUM
}

/**
 * Raw parsed intent from voice text. Deterministic, rule-based output
 * of PlaybackIntentParser.
 */
data class PlaybackIntent(
    val rawQuery: String,
    val target: String?,
    val appHint: String?,
    val typeHint: PlaybackType
)

/**
 * Resolved target after classification by PlaybackResolver.
 */
data class PlaybackTarget(
    val query: String,
    val type: PlaybackType
)
