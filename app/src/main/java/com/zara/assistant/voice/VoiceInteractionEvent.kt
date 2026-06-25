package com.zara.assistant.voice

/**
 * Layer 6.5G Phase 1 — VoiceInteractionEvent.
 * Phase 1 subset only — transcript/conversation-window events deferred.
 */
sealed class VoiceInteractionEvent {
    object MicStarted : VoiceInteractionEvent()
    object MicStopped : VoiceInteractionEvent()
    object VoiceReplyStarted : VoiceInteractionEvent()
    object VoiceReplyFinished : VoiceInteractionEvent()
}
