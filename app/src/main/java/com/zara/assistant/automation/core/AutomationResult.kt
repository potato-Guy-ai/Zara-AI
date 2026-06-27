package com.zara.assistant.automation.core

/**
 * Layer 6.6D Batch 0.2 — AutomationResult.
 *
 * Structured outcome of a finished automation session. Minimal on purpose —
 * expandable in later batches (no retry/timeout policy attached here).
 */
enum class AutomationResult {
    SUCCESS,
    FAILED,
    FAILED_CANCELLED,
    FAILED_TIMEOUT
}
