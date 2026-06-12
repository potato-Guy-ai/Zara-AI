package com.zara.assistant.workflow

import com.zara.assistant.core.ExecutionGuard
import com.zara.assistant.execution.ExecutionPlanner
import com.zara.assistant.execution.ExecutionQueue
import com.zara.assistant.execution.QueueItem
import com.zara.assistant.execution.TaskState
import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.5B — Workflow Engine.
 *
 * Responsibilities:
 *   1. Validate a WorkflowPlan (non-empty, dependencies consistent).
 *   2. Guard each step intent via ExecutionGuard.
 *   3. Convert each WorkflowStep into an ExecutionPlan with dependsOnId wired.
 *   4. Enqueue all plans into ExecutionQueue in order.
 *   5. Track workflow state transitions.
 *
 * Does NOT replace ExecutionQueue or ExecutionGuard — wraps them.
 * Does NOT execute intents — that remains in VoiceSessionManager/ActionExecutor.
 * Sequential only. No parallel execution. No background work.
 *
 * Execution order enforced by ExecutionQueue.dequeueNext() dependency checks
 * (dependsOnId → must be COMPLETED before dependent runs).
 */
object WorkflowEngine {

    private var idCounter = 0
    private fun nextPlanId() = "wp_${++idCounter}"

    /**
     * Validate, guard, and enqueue all steps of a WorkflowPlan.
     * Returns the WorkflowPlan with state updated to RUNNING,
     * or FAILED if validation fails.
     *
     * @param plan The WorkflowPlan produced by WorkflowPlanner.
     * @return     The same plan (mutated state field), ready to drain via ExecutionQueue.
     */
    fun submit(plan: WorkflowPlan): WorkflowPlan {
        if (!validate(plan)) {
            plan.state = WorkflowState.FAILED
            ZaraLogger.e("[WorkflowEngine] Validation failed for ${plan.workflowId}")
            return plan
        }

        plan.state = WorkflowState.RUNNING

        // Map stepId → ExecutionPlan.id so we can wire dependsOnId
        val stepIdToPlanId = mutableMapOf<String, String>()

        for (step in plan.steps) {
            val guardedIntent  = ExecutionGuard.guard(step.intent)
            val dependsOnPlanId = step.dependsOnStepId?.let { stepIdToPlanId[it] }

            val execPlan = ExecutionPlanner.plan(guardedIntent).copy(
                dependsOnId = dependsOnPlanId
            )

            stepIdToPlanId[step.stepId] = execPlan.id
            ExecutionQueue.enqueue(QueueItem(execPlan))

            ZaraLogger.d("[WorkflowEngine] enqueued step=${step.stepId} planId=${execPlan.id} dependsOn=$dependsOnPlanId")
        }

        ZaraLogger.d("[WorkflowEngine] ${plan.workflowId} submitted ${plan.steps.size} steps")
        return plan
    }

    /**
     * Mark workflow terminal based on queue outcomes.
     * Called after all queue items from this workflow have been drained.
     */
    fun finalize(plan: WorkflowPlan, queueItems: List<QueueItem>): WorkflowPlan {
        val anyFailed    = queueItems.any { it.state == TaskState.FAILED }
        val anyCancelled = queueItems.any { it.state == TaskState.CANCELLED }
        val allDone      = queueItems.all {
            it.state == TaskState.COMPLETED ||
            it.state == TaskState.FAILED    ||
            it.state == TaskState.CANCELLED
        }
        plan.state = when {
            !allDone     -> WorkflowState.RUNNING
            anyFailed    -> WorkflowState.FAILED
            anyCancelled -> WorkflowState.CANCELLED
            else         -> WorkflowState.COMPLETED
        }
        ZaraLogger.d("[WorkflowEngine] ${plan.workflowId} finalized -> ${plan.state}")
        return plan
    }

    // ── Validation ──────────────────────────────────────────────────────────

    private fun validate(plan: WorkflowPlan): Boolean {
        if (plan.steps.isEmpty()) return false
        val knownStepIds = mutableSetOf<String>()
        for (step in plan.steps) {
            val dep = step.dependsOnStepId
            if (dep != null && dep !in knownStepIds) {
                ZaraLogger.e("[WorkflowEngine] step ${step.stepId} depends on unknown step $dep")
                return false
            }
            knownStepIds.add(step.stepId)
        }
        return true
    }
}
