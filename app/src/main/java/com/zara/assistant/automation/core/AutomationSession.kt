package com.zara.assistant.automation.core

import java.util.UUID

/**
 * Layer 6.6D Batch 0.2 — AutomationSession.
 *
 * Represents one active automation job: identity + minimal lifecycle
 * state. No automation behavior lives here — just bookkeeping. State
 * transitions beyond simple start/stop/cancel are deferred to Batch 0.5.
 */
data class AutomationSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val request: AutomationRequest,
    val createdAt: Long = System.currentTimeMillis(),
    var state: SessionState = SessionState.IDLE
)
