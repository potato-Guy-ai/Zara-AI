package com.zara.assistant.media

/**
 * Layer 6.5F Phase 1 — Media control action types.
 * Maps voice commands to Android MediaSession transport actions.
 */
enum class MediaControlAction {
    PLAY,
    PAUSE,
    STOP,
    NEXT,
    PREVIOUS;

    companion object {
        /**
         * Map normalized voice text to a MediaControlAction.
         * Returns null if not a media control command.
         */
        fun fromText(text: String): MediaControlAction? {
            val t = text.trim().lowercase()
            return when {
                t == "play" || t == "resume" || t == "resume music"
                    || t == "play music" || t == "resume it" || t == "play it"    -> PLAY
                t == "pause" || t == "pause it" || t == "pause music"
                    || t == "pause the music"                                      -> PAUSE
                t == "stop" || t == "stop music" || t == "stop playing"
                    || t == "stop it"                                              -> STOP
                t == "next" || t == "next song" || t == "next track"
                    || t == "skip" || t == "skip song"                             -> NEXT
                t == "previous" || t == "previous song" || t == "previous track"
                    || t == "go back" || t == "last song"                          -> PREVIOUS
                else -> null
            }
        }
    }
}
