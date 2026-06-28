package com.zara.assistant.automation.workflow

/**
 * Layer 6.6D Batch 0.5 — AutomationWorkflow.
 * Pure data model: an ordered, app-agnostic list of steps. No logic.
 */
data class AutomationWorkflow(
    val workflowId: String,
    val steps: List<AutomationStep>
)
