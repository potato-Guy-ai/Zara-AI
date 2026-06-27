package com.zara.assistant.services

/**
 * Layer 6.6D Batch 0.1 — UI Automation Engine, service foundation.
 *
 * Lightweight, immutable snapshot of an AccessibilityEvent. This is the
 * only thing AccessibilityAutomationService forwards onward — no
 * AccessibilityNodeInfo, no UI tree, no business logic. Kept minimal on
 * purpose: future batches (UiAutomationEngine, Automation Session,
 * Automation Module) build on top of this, not the raw Android event.
 */
data class AutomationEvent(
    val eventType: Int,
    val packageName: String,
    val className: String?,
    val timestamp: Long
)
