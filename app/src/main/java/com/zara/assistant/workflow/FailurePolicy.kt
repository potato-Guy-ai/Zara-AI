package com.zara.assistant.workflow

/**
 * Layer 6.5B — Failure Policy.
 * Determines what happens when a WorkflowStep fails.
 *
 * STOP_WORKFLOW: default. Halt remaining steps on any failure.
 * CONTINUE:      proceed to next step regardless of failure.
 *
 * Retry logic is NOT implemented here — belongs to a future layer.
 */
enum class FailurePolicy {
    STOP_WORKFLOW,
    CONTINUE;

    companion object {
        val DEFAULT = STOP_WORKFLOW
    }
}
