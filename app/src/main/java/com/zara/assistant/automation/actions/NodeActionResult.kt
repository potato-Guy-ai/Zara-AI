package com.zara.assistant.automation.actions

/**
 * Layer 6.6D Batch 0.4 — NodeActionResult.
 * Structured result of a single node action (click).
 */
data class NodeActionResult(
    val status: NodeActionStatus,
    val message: String? = null
)
