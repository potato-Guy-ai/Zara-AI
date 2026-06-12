package com.zara.assistant.workflow

import com.zara.assistant.core.ExecutionGuard
import com.zara.assistant.core.IntentAction
import com.zara.assistant.core.IntentExtra
import com.zara.assistant.core.ZaraIntent
import com.zara.assistant.execution.ExecutionPlanner
import com.zara.assistant.execution.ExecutionQueue
import com.zara.assistant.execution.QueueItem
import com.zara.assistant.execution.TaskState
import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.5B + Completion Patch
 *
 * FIX 1: submit() injects WorkflowContextSnapshot person data into
 *   contact-based step intents that have no PHONE_NUMBER/CONTACT_NAME.
 *   This ensures "message him" in step 2 uses the snapshot-captured person
 *   rather than whatever live context exists at execution time.
 *
 * FIX 2: submit() returns WorkflowQueueHandle containing all planIds
 *   belonging to this workflow. cancelWorkflowItems(handle) cancels
 *   only workflow-owned PENDING items — no effect on unrelated queue items.
 */
object WorkflowEngine {

    private val CONTACT_ACTIONS = setOf(
        IntentAction.CALL,
        IntentAction.SEND_WHATSAPP,
        IntentAction.SEND_SMS
    )

    /** IDs of ExecutionPlans enqueued for one workflow run. */
    data class WorkflowQueueHandle(
        val workflowId: String,
        val planIds: List<String>
    )

    /**
     * Validate, guard, inject snapshot, and enqueue all steps.
     * Returns (mutated) plan + a handle of all enqueued planIds.
     */
    fun submit(plan: WorkflowPlan): Pair<WorkflowPlan, WorkflowQueueHandle> {
        if (!validate(plan)) {
            plan.state = WorkflowState.FAILED
            ZaraLogger.e("[WorkflowEngine] Validation failed for ${plan.workflowId}")
            return Pair(plan, WorkflowQueueHandle(plan.workflowId, emptyList()))
        }

        plan.state = WorkflowState.RUNNING

        val stepIdToPlanId = mutableMapOf<String, String>()
        val allPlanIds     = mutableListOf<String>()

        for (step in plan.steps) {
            // FIX 1: inject snapshot person into contact-based intents lacking resolution
            val enrichedIntent = injectSnapshotContext(step.intent, plan.contextSnapshot)
            val guardedIntent  = ExecutionGuard.guard(enrichedIntent)
            val dependsOnPlanId = step.dependsOnStepId?.let { stepIdToPlanId[it] }

            val execPlan = ExecutionPlanner.plan(guardedIntent).copy(
                dependsOnId = dependsOnPlanId
            )

            stepIdToPlanId[step.stepId] = execPlan.id
            allPlanIds.add(execPlan.id)
            ExecutionQueue.enqueue(QueueItem(execPlan))

            ZaraLogger.d("[WorkflowEngine] enqueued step=${step.stepId} planId=${execPlan.id} dependsOn=$dependsOnPlanId")
        }

        ZaraLogger.d("[WorkflowEngine] ${plan.workflowId} submitted ${plan.steps.size} steps")
        return Pair(plan, WorkflowQueueHandle(plan.workflowId, allPlanIds))
    }

    /**
     * FIX 2: Cancel all PENDING queue items owned by this workflow.
     * Called after STOP_WORKFLOW break. Does not touch unrelated items.
     */
    fun cancelWorkflowItems(handle: WorkflowQueueHandle) {
        val planIdSet = handle.planIds.toHashSet()
        ExecutionQueue.cancelByIds(planIdSet)
        ZaraLogger.d("[WorkflowEngine] cancelled remaining items for ${handle.workflowId}")
    }

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

    // ── FIX 1: Snapshot injection ───────────────────────────────────────────────

    private fun injectSnapshotContext(intent: ZaraIntent, snapshot: WorkflowContextSnapshot): ZaraIntent {
        // Only enrich contact-based intents that have no resolved contact yet
        if (intent.action !in CONTACT_ACTIONS) return intent
        if (intent.extra.containsKey(IntentExtra.PHONE_NUMBER)) return intent
        if (intent.extra.containsKey(IntentExtra.CONTACT_NAME)) return intent

        val person = snapshot.person ?: return intent
        if (person.isExpired()) return intent

        val newExtra = intent.extra.toMutableMap()
        newExtra[IntentExtra.CONTACT_NAME] = person.contactName
        if (person.phoneNumber != null) {
            newExtra[IntentExtra.PHONE_NUMBER] = person.phoneNumber
        }
        ZaraLogger.d("[WorkflowEngine] snapshot injected person=${person.contactName} into ${intent.action}")
        return intent.copy(extra = newExtra)
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
