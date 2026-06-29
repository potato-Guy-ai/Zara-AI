package com.zara.assistant.playback

/**
 * Layer 6.6A Phase 1 — DeepLinkManager.
 *
 * Foundation only. Generates Spotify deep link URIs as strings.
 * No execution. No Intent launch. No Context usage.
 */
object DeepLinkManager {

    fun generate(target: PlaybackTarget): String? {
        val encoded = target.query.trim()
        if (encoded.isBlank()) return null
        return when (target.type) {
            PlaybackType.SONG, PlaybackType.RECOMMENDATION ->
                "spotify:track:search:${encode(encoded)}"
            PlaybackType.PLAYLIST ->
                "spotify:playlist:search:${encode(encoded)}"
            PlaybackType.ALBUM ->
                "spotify:album:search:${encode(encoded)}"
            PlaybackType.ARTIST ->
                "spotify:artist:search:${encode(encoded)}"
            PlaybackType.LIKED ->
                "spotify:track:liked"
            PlaybackType.UNKNOWN -> null
        }
    }

    private fun encode(text: String): String =
        text.replace(" ", "+")
}
