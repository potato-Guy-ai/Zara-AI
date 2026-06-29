package com.zara.assistant.streaming

/**
 * Layer 6.5E — Interaction Event Model.
 *
 * Sealed hierarchy — one instance per event type.
 * All events are data-only. No execution logic.
 */
sealed class ZaraInteractionEvent {

    // ── STT events ──────────────────────────────────────────────────────
    object ListeningStarted                       : ZaraInteractionEvent()
    object ListeningStopped                       : ZaraInteractionEvent()
    data class PartialStt(val text: String)       : ZaraInteractionEvent()
    data class FinalStt(val text: String)         : ZaraInteractionEvent()

    // ── Resolution events ────────────────────────────────────────────────
    data class ContactResolutionStarted(val query: String)   : ZaraInteractionEvent()
    data class ContactResolutionCompleted(val name: String?) : ZaraInteractionEvent()
    data class AppResolutionStarted(val query: String)       : ZaraInteractionEvent()
    data class AppResolutionCompleted(val pkg: String?)      : ZaraInteractionEvent()

    // ── Clarification / confirmation / recovery ──────────────────────────
    data class ClarificationRequired(val candidates: List<String>) : ZaraInteractionEvent()
    data class ConfirmationRequired(val prompt: String)            : ZaraInteractionEvent()
    object RecoveryRequired                                        : ZaraInteractionEvent()

    // ── Workflow events ──────────────────────────────────────────────────
    data class WorkflowStarted(val stepCount: Int)                     : ZaraInteractionEvent()
    data class WorkflowStepStarted(val stepIndex: Int, val action: String) : ZaraInteractionEvent()
    data class WorkflowStepCompleted(val stepIndex: Int, val result: String) : ZaraInteractionEvent()
    data class WorkflowCompleted(val results: List<String>)            : ZaraInteractionEvent()

    // ── Execution events ─────────────────────────────────────────────────
    data class ExecutionStarted(val action: String)         : ZaraInteractionEvent()
    data class ExecutionCompleted(val result: String)       : ZaraInteractionEvent()
    data class ExecutionFailed(val reason: String)          : ZaraInteractionEvent()

    // ── Pipeline state ───────────────────────────────────────────────────
    data class PipelineStateChanged(val state: PipelineState) : ZaraInteractionEvent()
}
