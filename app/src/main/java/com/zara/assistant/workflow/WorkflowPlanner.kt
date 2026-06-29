package com.zara.assistant.workflow

import com.zara.assistant.core.ZaraIntent
import java.util.UUID

/**
 * Layer 6.5B — Workflow Planner.
 *
 * Input:  ordered sequence of ZaraIntents (from CompoundIntentSplitter + pipeline).
 * Output: WorkflowPlan with explicit step dependencies.
 *
 * Dependency rule:
 *   Each step n depends on step n-1 (sequential chain).
 *   Step 0 has no dependency.
 *
 * Examples:
 *   [OPEN_APP(youtube), SEARCH_QUERY(cats)]
 *     step_1: OPEN_APP   dependsOn=null
 *     step_2: SEARCH     dependsOn=step_1
 *
 *   [OPEN_APP(spotify), PLAY_MUSIC(believer)]
 *     step_1: OPEN_APP   dependsOn=null
 *     step_2: PLAY_MUSIC dependsOn=step_1
 *
 *   [CALL(boss), SEND_WHATSAPP(boss)]
 *     step_1: CALL         dependsOn=null
 *     step_2: SEND_WHATSAPP dependsOn=step_1
 *
 * Single-intent inputs return a trivial 1-step workflow.
 * O(n) — no sorting, no graph traversal.
 */
object WorkflowPlanner {

    fun plan(intents: List<ZaraIntent>): WorkflowPlan {
        require(intents.isNotEmpty()) { "Cannot plan empty intent list." }

        val workflowId = "wf_${UUID.randomUUID().toString().take(8)}"
        val snapshot   = WorkflowContextSnapshot.capture()

        val steps = mutableListOf<WorkflowStep>()
        var previousStepId: String? = null

        intents.forEachIndexed { index, intent ->
            val stepId = "${workflowId}_s${index + 1}"
            steps.add(
                WorkflowStep(
                    workflowId      = workflowId,
                    stepId          = stepId,
                    intent          = intent,
                    stepType        = WorkflowStepType.ACTION,
                    dependsOnStepId = previousStepId,
                    failurePolicy   = FailurePolicy.DEFAULT
                )
            )
            previousStepId = stepId
        }

        return WorkflowPlan(
            workflowId      = workflowId,
            steps           = steps,
            contextSnapshot = snapshot
        )
    }
}
