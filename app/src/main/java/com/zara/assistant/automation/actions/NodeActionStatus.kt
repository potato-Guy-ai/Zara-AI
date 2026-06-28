package com.zara.assistant.automation.actions

/**
 * Layer 6.6D Batch 0.4 — NodeActionStatus.
 * Structured outcome of a single node action (click).
 */
enum class NodeActionStatus {
    SUCCESS,
    FAILED_INVALID_NODE,
    FAILED_NOT_CLICKABLE,
    FAILED_ACTION_FAILED
}
