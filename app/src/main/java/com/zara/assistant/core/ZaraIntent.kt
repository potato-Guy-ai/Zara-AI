package com.zara.assistant.core

/**
 * Structured intent passed through the pipeline.
 * Voice/Text → STT → CorrectionLayer → IntentRouter → ActionExecutor
 */
data class ZaraIntent(
    val type: IntentType,
    val action: String,           // e.g. "OPEN_APP", "CALL", "SET_WIFI"
    val target: String? = null,   // e.g. "WhatsApp", "Amma"
    val extra: Map<String, String> = emptyMap(),
    val rawText: String = "",
    val confidence: Float = 1.0f
)

enum class IntentType {
    ACTION,         // local device action
    CONVERSATION,   // chat/query response
    CLOUD,          // needs cloud reasoning
    UNKNOWN
}
