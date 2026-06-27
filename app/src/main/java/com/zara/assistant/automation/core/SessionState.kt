package com.zara.assistant.automation.core

/**
 * Layer 6.6D Batch 0.2 — SessionState.
 *
 * Minimal lifecycle states for an AutomationSession. No advanced state
 * machine (transition guards, sub-states, etc.) — that belongs to Batch 0.5.
 */
enum class SessionState {
    IDLE,
    ACTIVE,
    COMPLETED,
    FAILED,
    CANCELLED
}
