package com.zara.assistant.core

import com.zara.assistant.core.IntentExtra
import com.zara.assistant.core.ZaraIntent

/**
 * Layer 5 Hardening — App Action Planner (updated).
 *
 * Improvements:
 * - Channel detection: PHONE / WHATSAPP / TELEGRAM / INSTAGRAM / MESSENGER
 * - WhatsApp action hardening: voice message, video call, message body vs recipient
 * - PreferredAppRegistry bias for app detection
 * - Intent sanity check: unsupported commands detected
 */
object AppActionPlanner {

    const val KEY_APP    = "app_plan_app"
    const val KEY_ACTION = "app_plan_action"
    const val KEY_TARGET = "app_plan_target"
    const val KEY_QUERY  = "app_plan_query"
    const val KEY_MODE   = "app_plan_mode"
    const val KEY_CHANNEL = "app_plan_channel"

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

    // Unsupported command keywords
    private val UNSUPPORTED_KEYWORDS = setOf(
        "destroy", "hack", "delete system", "format", "root", "wipe"
    )

    fun plan(intent: ZaraIntent): ZaraIntent {
        val raw = intent.rawText.lowercase()

        // Sanity check
        if (UNSUPPORTED_KEYWORDS.any { raw.contains(it) }) {
            val newExtra = intent.extra.toMutableMap()
            newExtra["unsupported_command"] = "true"
            return intent.copy(extra = newExtra)
        }

        val app = detectApp(raw, intent) ?: return intent

        val channel = detectChannel(raw)
        val action  = detectAction(app, raw, channel)
        val target  = intent.extra[IntentExtra.CONTACT_NAME]
            ?: intent.extra[IntentExtra.RECIPIENT]
            ?: intent.target
        // Extract body: after "saying" — do NOT treat as recipient
        val body = extractBody(raw)
        val query = body
            ?: intent.extra[IntentExtra.QUERY]
            ?: intent.extra[IntentExtra.CONTENT]

        val newExtra = intent.extra.toMutableMap()
        newExtra[KEY_APP]    = app
        newExtra[KEY_ACTION] = action
        if (channel != null) newExtra[KEY_CHANNEL] = channel
        if (target != null)  newExtra[KEY_TARGET]  = target
        if (query  != null)  newExtra[KEY_QUERY]   = query
        return intent.copy(extra = newExtra)
    }

    private fun detectChannel(raw: String): String? = when {
        raw.contains("on whatsapp") || raw.contains("via whatsapp") -> "whatsapp"
        raw.contains("on telegram") || raw.contains("via telegram") -> "telegram"
        raw.contains("on instagram")                               -> "instagram"
        raw.contains("on messenger")                               -> "messenger"
        else -> null
    }

    private fun detectApp(raw: String, intent: ZaraIntent): String? {
        val pkg = intent.extra[IntentExtra.APP_PACKAGE] ?: ""
        val app = PreferredAppRegistry.preferred(intent.extra[IntentExtra.APP] ?: "")
        return when {
            raw.contains("whatsapp") || app == "whatsapp" || pkg.contains("whatsapp") -> APP_WHATSAPP
            raw.contains("youtube")  || app == "youtube"  || pkg.contains("youtube")  -> APP_YOUTUBE
            raw.contains("spotify")  || app == "spotify"  || pkg.contains("spotify")  -> APP_MUSIC
            raw.contains(" music")   || app == "spotify"  || pkg.contains("music")    -> APP_MUSIC
            raw.contains(" call ")   || raw.startsWith("call ") || raw.endsWith(" call") -> APP_PHONE
            else -> null
        }
    }

    private fun detectAction(app: String, raw: String, channel: String?): String = when (app) {
        APP_WHATSAPP -> when {
            raw.contains("voice message") || raw.contains("voice msg") -> ACTION_VOICE_MESSAGE
            raw.contains("video call")                                 -> ACTION_VIDEO_CALL
            raw.contains("audio call")                                 -> ACTION_AUDIO_CALL
            raw.contains("open chat")                                  -> ACTION_OPEN_CHAT
            raw.contains("call")                                       -> ACTION_AUDIO_CALL
            raw.contains("message") || raw.contains("msg") ||
            raw.contains("send")                                       -> ACTION_MESSAGE
            else                                                       -> ACTION_OPEN_CHAT
        }
        APP_YOUTUBE -> when {
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

    // Extract message body (text after "saying") — must NOT bleed into contact detection
    private fun extractBody(raw: String): String? {
        val markers = listOf(" saying ", " with message ", " that says ")
        for (m in markers) {
            val idx = raw.indexOf(m)
            if (idx >= 0) {
                val body = raw.substring(idx + m.length).trim()
                if (body.isNotBlank()) return body
            }
        }
        return null
    }
}
