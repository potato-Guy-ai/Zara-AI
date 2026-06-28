package com.zara.assistant.automation.workflow

/**
 * Layer 6.6D Batch 0.5 — WorkflowResult.
 */
data class WorkflowResult(
    val status: WorkflowStatus,
    val message: String? = null
)
