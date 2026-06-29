package com.zara.assistant.voice

/**
 * Layer 6.6 — Voice Conversation Loop.
 * INACTIVE: normal wakeword-required mode.
 * ACTIVE:   live multi-turn conversation window (no wakeword needed for follow-ups).
 */
enum class ConversationModeState {
    INACTIVE,
    ACTIVE
}
