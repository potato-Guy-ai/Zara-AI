package com.zara.assistant.voice

/**
 * Layer 6.5G Phase 1 — VoiceInteractionManager.
 * Holds current InteractionMode + active voice session flag, publishes
 * events to listeners. No timers, no polling, no services. Not wired
 * into VoiceSessionManager/MainActivity yet — additive only.
 */
object VoiceInteractionManager {

    @Volatile
    var currentMode: InteractionMode = InteractionMode.TEXT_MODE
        private set

    @Volatile
    var isVoiceSessionActive: Boolean = false
        private set

    private val listeners = mutableListOf<(VoiceInteractionEvent) -> Unit>()

    fun addListener(listener: (VoiceInteractionEvent) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (VoiceInteractionEvent) -> Unit) {
        listeners.remove(listener)
    }

    fun setMode(mode: InteractionMode) {
        currentMode = mode
    }

    fun onMicStarted() {
        isVoiceSessionActive = true
        publish(VoiceInteractionEvent.MicStarted)
    }

    fun onMicStopped() {
        isVoiceSessionActive = false
        publish(VoiceInteractionEvent.MicStopped)
    }

    fun onVoiceReplyStarted() {
        publish(VoiceInteractionEvent.VoiceReplyStarted)
    }

    fun onVoiceReplyFinished() {
        publish(VoiceInteractionEvent.VoiceReplyFinished)
    }

    private fun publish(event: VoiceInteractionEvent) {
        listeners.forEach { it(event) }
    }
}
