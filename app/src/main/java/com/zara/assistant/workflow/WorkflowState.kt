package com.zara.assistant.workflow

/**
 * Layer 6.5B — Workflow State Machine.
 * Mirrors TaskState but scoped to the workflow lifecycle.
 */
enum class WorkflowState {
    PENDING,
    RUNNING,
    WAITING,
    COMPLETED,
    FAILED,
    CANCELLED
}
