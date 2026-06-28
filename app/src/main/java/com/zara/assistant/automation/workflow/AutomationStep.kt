package com.zara.assistant.automation.workflow

/**
 * Layer 6.6D Batch 0.5 — AutomationStep.
 * Generic step definition. Examples:
 *   WAIT_FOR_PACKAGE -> value = package name
 *   FIND_BY_TEXT      -> value = search text
 *   SCAN_NODES / CLICK_MATCH -> value unused (null)
 */
data class AutomationStep(
    val type: AutomationStepType,
    val value: String? = null
)
