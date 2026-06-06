package com.zara.assistant.core

/**
 * Layer 5.6 — App Action Planner.
 * Pure rule-based, stateless, deterministic.
 */
object AppActionPlanner {

    const val KEY_APP    = "app_plan_app"
    const val KEY_ACTION = "app_plan_action"
    const val KEY_TARGET = "app_plan_target"
    const val KEY_QUERY  = "app_plan_query"
    const val KEY_MODE   = "app_plan_mode"

    private const val APP_WHATSAPP = "whatsapp"
    private const val APP_YOUTUBE  = "youtube"
    private const val APP_PHONE    = "phone"
    private const val APP_MUSIC    = "music"

    const val ACTION_MESSAGE       = "message"
    const val ACTION_VOICE_MESSAGE = "voice_message"
    const val ACTION_VIDEO_CALL    = "video_call"
    const val ACTION_AUDIO_CALL    = "audio_call"
    const val ACTION_OPEN_CHAT     = "open_chat"
    const val ACTION_SEARCH        = "search"
    const val ACTION_PLAY          = "play"
    const val ACTION_OPEN_VIDEO    = "open_video"
    const val ACTION_CALL          = "call"
    const val ACTION_PLAY_SONG     = "play_song"
    const val ACTION_PLAY_ARTIST   = "play_artist"
    const val ACTION_PLAY_PLAYLIST = "play_playlist"

    fun plan(intent: ZaraIntent): ZaraIntent {
        val raw = intent.rawText.lowercase()
        val app = detectApp(raw, intent) ?: return intent

        val action = detectAction(app, raw)
        val target = intent.extra[IntentExtra.CONTACT_NAME]
            ?: intent.extra[IntentExtra.RECIPIENT]
            ?: intent.target
        val query  = intent.extra[IntentExtra.QUERY]
            ?: intent.extra[IntentExtra.CONTENT]

        val newExtra = intent.extra.toMutableMap()
        newExtra[KEY_APP]    = app
        newExtra[KEY_ACTION] = action
        if (target != null) newExtra[KEY_TARGET] = target
        if (query  != null) newExtra[KEY_QUERY]  = query
        return intent.copy(extra = newExtra)
    }

    private fun detectApp(raw: String, intent: ZaraIntent): String? {
        val pkg = intent.extra[IntentExtra.APP_PACKAGE] ?: ""
        val app = intent.extra[IntentExtra.APP]?.lowercase() ?: ""
        return when {
            raw.contains("whatsapp") || app.contains("whatsapp") || pkg.contains("whatsapp") -> APP_WHATSAPP
            raw.contains("youtube")  || app.contains("youtube")  || pkg.contains("youtube")  -> APP_YOUTUBE
            raw.contains("spotify")  || app.contains("spotify")  || pkg.contains("spotify")  -> APP_MUSIC
            raw.contains(" music")   || app.contains("music")    || pkg.contains("music")    -> APP_MUSIC
            raw.contains(" call ")   || raw.startsWith("call ")  || raw.endsWith(" call")    -> APP_PHONE
            else -> null
        }
    }

    private fun detectAction(app: String, raw: String): String = when (app) {
        APP_WHATSAPP -> when {
            raw.contains("voice message") || raw.contains("voice msg") -> ACTION_VOICE_MESSAGE
            raw.contains("video call")                                 -> ACTION_VIDEO_CALL
            raw.contains("audio call")                                 -> ACTION_AUDIO_CALL
            raw.contains("open chat")                                  -> ACTION_OPEN_CHAT
            raw.contains("call")                                       -> ACTION_AUDIO_CALL
            raw.contains("message") || raw.contains("msg")             -> ACTION_MESSAGE
            else                                                       -> ACTION_OPEN_CHAT
        }
        APP_YOUTUBE  -> when {
            raw.contains("search") -> ACTION_SEARCH
            raw.contains("play")   -> ACTION_PLAY
            raw.contains("open")   -> ACTION_OPEN_VIDEO
            else                   -> ACTION_SEARCH
        }
        APP_PHONE -> ACTION_CALL
        APP_MUSIC -> when {
            raw.contains("artist")   -> ACTION_PLAY_ARTIST
            raw.contains("playlist") -> ACTION_PLAY_PLAYLIST
            else                     -> ACTION_PLAY_SONG
        }
        else -> ACTION_PLAY
    }
}
