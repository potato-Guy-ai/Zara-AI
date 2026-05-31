package com.zara.assistant.core

/**
 * Structured intent passed through the pipeline.
 * Voice/Text → STT → CorrectionLayer → LocalIntentClassifier → IntentRouter → ActionExecutor
 */
data class ZaraIntent(
    val type: IntentType,
    val action: String,
    val target: String? = null,
    val extra: Map<String, String> = emptyMap(),
    val rawText: String = "",
    val confidence: Float = 1.0f,          // reserved for ML classifier integration
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String = ""
)

enum class IntentType {
    ACTION,       // local device action
    CONVERSATION, // handled locally (time, date, greetings)
    CLOUD,        // requires cloud reasoning
    UNKNOWN
}

/** Typed action constants — prevents typo-based silent failures in extra map keys. */
object IntentAction {
    const val CALL           = "CALL"
    const val ANSWER_CALL    = "ANSWER_CALL"
    const val END_CALL       = "END_CALL"
    const val SEND_SMS       = "SEND_SMS"
    const val OPEN_APP       = "OPEN_APP"
    const val OPEN_CAMERA    = "OPEN_CAMERA"
    const val SET_ALARM      = "SET_ALARM"
    const val SET_TIMER      = "SET_TIMER"
    const val SET_WIFI       = "SET_WIFI"
    const val SET_BLUETOOTH  = "SET_BLUETOOTH"
    const val SET_FLASHLIGHT = "SET_FLASHLIGHT"
    const val SET_VOLUME     = "SET_VOLUME"
    const val SET_SILENT     = "SET_SILENT"
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

/** Typed extra keys — prevents silent failures from key typos. */
object IntentExtra {
    const val ON        = "on"      // Boolean string: "true" / "false"
    const val DIRECTION = "dir"     // "up" / "down"
    const val BODY      = "body"    // SMS message body
    const val DURATION  = "duration" // timer/alarm seconds
}
