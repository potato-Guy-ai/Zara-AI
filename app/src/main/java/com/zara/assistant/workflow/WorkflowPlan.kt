package com.zara.assistant.workflow

/**
 * Layer 6.5B — A complete workflow: an ordered list of WorkflowSteps.
 *
 * @param workflowId       Unique identifier for this workflow run.
 * @param steps            Ordered list of steps. Must be non-empty.
 * @param contextSnapshot  Read-only context captured at workflow creation time.
 * @param state            Overall workflow lifecycle state.
 * @param failurePolicy    Default policy; individual steps may override.
 */
data class WorkflowPlan(
    val workflowId: String,
    val steps: List<WorkflowStep>,
    val contextSnapshot: WorkflowContextSnapshot,
    var state: WorkflowState = WorkflowState.PENDING,
    val failurePolicy: FailurePolicy = FailurePolicy.DEFAULT
) {
    init {
        require(steps.isNotEmpty()) { "WorkflowPlan must have at least one step." }
        require(steps.all { it.workflowId == workflowId }) { "All steps must belong to this workflow." }
    }

    val currentStep: WorkflowStep?
        get() = steps.firstOrNull { it.state == WorkflowState.PENDING || it.state == WorkflowState.RUNNING }

    val isTerminal: Boolean
        get() = state == WorkflowState.COMPLETED ||
                state == WorkflowState.FAILED    ||
                state == WorkflowState.CANCELLED
}
