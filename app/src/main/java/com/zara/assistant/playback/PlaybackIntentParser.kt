package com.zara.assistant.playback

/**
 * Layer 6.6A Phase 1 — PlaybackIntentParser.
 *
 * Deterministic, rule-based extraction only. No AI. No ML.
 *
 * Input examples:
 *   play believer
 *   play my gym playlist
 *   play liked songs
 *   play arijit songs
 *   play relaxing music
 *
 * Output: PlaybackIntent (raw query, target, app hint, type hint).
 */
object PlaybackIntentParser {

    private val PLAY_TRIGGERS = listOf("play ")

    private val LIKED_PATTERNS = listOf(
        "liked songs", "my liked songs", "liked music"
    )

    private val PLAYLIST_MARKERS = listOf("playlist")
    private val ALBUM_MARKERS    = listOf("album")
    private val ARTIST_MARKERS  = listOf("songs", "tracks", "music by")

    // Layer 6.6A patch — recommendation-style queries must classify as
    // RECOMMENDATION, not fall through to SONG/ARTIST. Checked before
    // ARTIST_MARKERS since "sad songs" / "happy songs" contain "songs".
    private val RECOMMENDATION_PATTERNS = listOf(
        "relaxing music", "chill music", "sad songs", "happy songs",
        "workout music", "focus music", "sleep music",
        "something relaxing", "something chill",
        "my taste", "based on my taste"
    )

    private val APP_HINTS = mapOf(
        "spotify" to "spotify",
        "youtube music" to "youtube_music",
        "youtube" to "youtube",
        "gaana" to "gaana",
        "jiosaavn" to "jiosaavn"
    )

    fun parse(rawText: String): PlaybackIntent {
        val lower = rawText.trim().lowercase()

        val withoutTrigger = stripTrigger(lower)

        val appHint = APP_HINTS.entries.firstOrNull { withoutTrigger.contains(it.key) }?.value
        val cleaned = appHint?.let { hint ->
            APP_HINTS.entries.firstOrNull { it.value == hint }?.key?.let { key ->
                withoutTrigger.replace(key, "").trim()
            }
        } ?: withoutTrigger

        val typeHint = detectType(cleaned)
        val target = extractTarget(cleaned, typeHint)

        return PlaybackIntent(
            rawQuery = rawText,
            target = target,
            appHint = appHint,
            typeHint = typeHint
        )
    }

    private fun stripTrigger(text: String): String {
        for (trigger in PLAY_TRIGGERS) {
            if (text.startsWith(trigger)) return text.removePrefix(trigger).trim()
        }
        return text
    }

    private fun detectType(text: String): PlaybackType {
        return when {
            LIKED_PATTERNS.any { text.contains(it) } -> PlaybackType.LIKED
            RECOMMENDATION_PATTERNS.any { text.contains(it) } -> PlaybackType.RECOMMENDATION
            PLAYLIST_MARKERS.any { text.contains(it) } -> PlaybackType.PLAYLIST
            ALBUM_MARKERS.any { text.contains(it) } -> PlaybackType.ALBUM
            ARTIST_MARKERS.any { text.contains(it) } -> PlaybackType.ARTIST
            text.isNotBlank() -> PlaybackType.SONG
            else -> PlaybackType.UNKNOWN
        }
    }

    private fun extractTarget(text: String, type: PlaybackType): String? {
        if (text.isBlank()) return null
        return when (type) {
            PlaybackType.LIKED -> null
            PlaybackType.PLAYLIST -> text.replace("playlist", "").trim().ifBlank { null }
            PlaybackType.ALBUM -> text.replace("album", "").trim().ifBlank { null }
            PlaybackType.ARTIST -> {
                var t = text
                for (m in ARTIST_MARKERS) t = t.replace(m, "")
                t.trim().ifBlank { null }
            }
            PlaybackType.SONG -> text.trim().ifBlank { null }
            PlaybackType.UNKNOWN -> null
            PlaybackType.RECOMMENDATION -> text.trim().ifBlank { null }
        }
    }
}
