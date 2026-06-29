package com.zara.assistant.voice

/**
 * Layer 6.5G Phase 1 — InteractionMode.
 * TEXT_MODE: typed input. VOICE_ACTION_MODE: voice action command.
 * VOICE_CONVERSATION_MODE: voice question/query.
 */
enum class InteractionMode {
    TEXT_MODE,
    VOICE_ACTION_MODE,
    VOICE_CONVERSATION_MODE
}
