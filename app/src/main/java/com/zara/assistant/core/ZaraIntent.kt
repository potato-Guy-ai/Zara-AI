package com.zara.assistant.core

/**
 * Structured intent passed through the pipeline.
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
    const val CHANNEL   = "channel"   // "sms" | "whatsapp"
    const val MODE      = "mode"      // "silent" | "vibrate" | "normal"
}

object ChannelType {
    const val SMS      = "sms"
    const val WHATSAPP = "whatsapp"
}
