package com.zara.assistant.core

/**
 * B2.2 + Layer 6.5F Phase 1
 *
 * Added: MEDIA_CONTROL action constant.
 * Added: MEDIA_ACTION extra key.
 */
data class ZaraIntent(
    val type: IntentType,
    val action: String,
    val target: String? = null,
    val extra: Map<String, String> = emptyMap(),
    val rawText: String = "",
    val confidence: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String = ""
)

enum class IntentType {
    ACTION,
    CONVERSATION,
    CLOUD,
    UNKNOWN
}

object IntentAction {
    const val CALL           = "CALL"
    const val ANSWER_CALL    = "ANSWER_CALL"
    const val END_CALL       = "END_CALL"
    const val SEND_SMS       = "SEND_SMS"
    const val SEND_WHATSAPP  = "SEND_WHATSAPP"
    const val OPEN_APP       = "OPEN_APP"
    const val OPEN_CAMERA    = "OPEN_CAMERA"
    const val SET_ALARM      = "SET_ALARM"
    const val SET_TIMER      = "SET_TIMER"
    const val SET_WIFI       = "SET_WIFI"
    const val SET_BLUETOOTH  = "SET_BLUETOOTH"
    const val SET_FLASHLIGHT = "SET_FLASHLIGHT"
    const val SET_VOLUME     = "SET_VOLUME"
    const val SET_SILENT     = "SET_SILENT"
    const val SET_RINGER     = "SET_RINGER"
    const val LOCK_SCREEN    = "LOCK_SCREEN"
    const val NAVIGATE_TO    = "NAVIGATE_TO"
    const val PLAY_MUSIC     = "PLAY_MUSIC"
    const val SEARCH_QUERY   = "SEARCH_QUERY"
    const val SHOW_ALARMS    = "SHOW_ALARMS"
    const val SHOW_TIMERS    = "SHOW_TIMERS"
    const val OPEN_CLOCK     = "OPEN_CLOCK"
    // Layer 6.5F Phase 1: media transport control
    const val MEDIA_CONTROL  = "MEDIA_CONTROL"
    const val TIME           = "TIME"
    const val DATE           = "DATE"
    const val GREETING       = "GREETING"
    const val STOP           = "STOP"
    const val QUERY          = "QUERY"
    const val UNKNOWN        = "UNKNOWN"
}

object IntentExtra {
    const val ON        = "on"
    const val DIRECTION = "dir"
    const val BODY      = "body"
    const val DURATION  = "duration"
    const val CHANNEL   = "channel"
    const val MODE      = "mode"
    const val APP         = "app"
    const val SONG        = "song"
    const val ARTIST      = "artist"
    const val TIME        = "time"
    const val ALARM_HOUR   = "alarm_hour"
    const val ALARM_MINUTE = "alarm_minute"
    const val DESTINATION = "destination"
    const val QUERY       = "query"
    const val CONTENT     = "content"
    const val RECIPIENT   = "recipient"
    const val CONTACT_NAME       = "contact_name"
    const val PHONE_NUMBER       = "phone_number"
    const val APP_PACKAGE        = "app_package"
    const val APP_NAME           = "app_name"
    const val ENTITY_CONFIDENCE  = "entity_confidence"
    const val ENTITY_CANDIDATES  = "entity_candidates"
    const val NEEDS_CLARIFICATION = "needs_clarification"
    // Layer 6.5F Phase 1
    const val MEDIA_ACTION = "media_action"  // stores MediaControlAction.name()
}

object ChannelType {
    const val SMS       = "sms"
    const val WHATSAPP  = "whatsapp"
    const val TELEGRAM  = "telegram"
    const val SIGNAL    = "signal"
    const val MESSENGER = "messenger"
    const val DISCORD   = "discord"
}
