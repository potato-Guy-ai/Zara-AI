package com.zara.assistant.streaming

/**
 * Layer 6.5E — Pipeline State Machine.
 *
 * Single active state. Explicit transitions only.
 * No timers. No background work. Purely observational.
 */
enum class PipelineState {
    LISTENING,
    PROCESSING,
    RESOLVING_CONTACT,
    RESOLVING_APP,
    WAITING_CLARIFICATION,
    WAITING_CONFIRMATION,
    WAITING_RECOVERY,
    WORKFLOW_RUNNING,
    EXECUTING,
    COMPLETED,
    FAILED;
}

/**
 * Singleton state holder — single active state, updated inline by VoiceSessionManager.
 * Read by UI layer only.
 */
object PipelineStateMachine {
    @Volatile
    var current: PipelineState = PipelineState.LISTENING
        private set

    fun transition(state: PipelineState) {
        current = state
        InteractionEventPublisher.publish(ZaraInteractionEvent.PipelineStateChanged(state))
    }
}
