package com.zara.assistant.workflow

import com.zara.assistant.core.ZaraIntent

/**
 * Layer 6.5B — A single step within a WorkflowPlan.
 *
 * @param workflowId     Parent workflow identifier.
 * @param stepId         Unique step identifier within the workflow.
 * @param intent         The ZaraIntent to execute for this step.
 * @param stepType       Step type: ACTION (initially), CONFIRMATION, WAIT (future-proofed).
 * @param dependsOnStepId Explicit dependency: this step waits for the named step to complete.
 * @param failurePolicy  What to do when this step fails.
 * @param state          Current lifecycle state.
 */
data class WorkflowStep(
    val workflowId: String,
    val stepId: String,
    val intent: ZaraIntent,
    val stepType: WorkflowStepType = WorkflowStepType.ACTION,
    val dependsOnStepId: String? = null,
    val failurePolicy: FailurePolicy = FailurePolicy.DEFAULT,
    var state: WorkflowState = WorkflowState.PENDING
)

enum class WorkflowStepType {
    ACTION,       // Execute a ZaraIntent immediately.
    CONFIRMATION, // Wait for user confirmation before proceeding. (future)
    WAIT          // Explicit pause step. (future)
}
