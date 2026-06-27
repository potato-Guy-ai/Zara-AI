package com.zara.assistant.voice

import com.zara.assistant.streaming.InteractionEventPublisher
import com.zara.assistant.streaming.ZaraInteractionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Layer 6.5G Phase 2 — STT Streaming UI Bridge.
 *
 * Subscribes to InteractionEventPublisher.
 * Updates transcript on PartialStt and FinalStt.
 * Clears on ListeningStarted.
 * Clears on ExecutionCompleted/ExecutionFailed (BUG 2 fix — transcript must
 * clear once the command's response has been delivered).
 * No polling, no timers, no threads.
 */
object StreamingTranscriptManager {

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript

    private val observer: (ZaraInteractionEvent) -> Unit = { event ->
        when (event) {
            is ZaraInteractionEvent.ListeningStarted   -> _transcript.value = ""
            is ZaraInteractionEvent.PartialStt         -> _transcript.value = event.text
            is ZaraInteractionEvent.FinalStt           -> _transcript.value = event.text
            is ZaraInteractionEvent.ExecutionCompleted -> _transcript.value = ""
            is ZaraInteractionEvent.ExecutionFailed    -> _transcript.value = ""
            else                                       -> Unit
        }
    }

    fun register() {
        InteractionEventPublisher.observe(observer)
    }

    fun unregister() {
        InteractionEventPublisher.removeObserver(observer)
        _transcript.value = ""
    }
}
