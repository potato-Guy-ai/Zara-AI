package com.zara.assistant.automation.core

import com.zara.assistant.automation.workflow.WorkflowExecutor
import com.zara.assistant.services.AccessibilityAutomationService
import com.zara.assistant.services.AutomationEvent
import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.6D Batch 0.2 — UiAutomationEngine.
 *
 * The "brain" at the top of the automation pipeline:
 *   Automation Request -> UiAutomationEngine -> Automation Session ->
 *   Automation Module -> AccessibilityAutomationService -> Android UI
 *
 * This batch wires session lifecycle + event routing ONLY. No node
 * scanning, no rootInActiveWindow access, no UI inspection, no click
 * automation, no matcher/policy/retry logic — those belong to later
 * batches (Automation Module onward, Batch 0.5+).
 *
 * Single-session policy (mandatory): only one AutomationSession may be
 * active at a time. start() cancels any existing session first.
 *
 * Layer 6.6D Batch 0.5A — handleEvent() now also forwards each event to
 * WorkflowExecutor.onEvent(), so a running workflow's WAIT_FOR_PACKAGE
 * step can actually be satisfied. Routing only — no other change.
 */
object UiAutomationEngine {

    private var activeSession: AutomationSession? = null

    /**
     * Subscribes this engine to the accessibility service's event hook
     * (Batch 0.1's setAutomationEventListener). Safe to call repeatedly.
     * No-op if the service hasn't connected yet — call again once it has
     * (e.g. from AccessibilityAutomationService.onServiceConnected()).
     */
    fun attach() {
        AccessibilityAutomationService.instance?.setAutomationEventListener { event ->
            handleEvent(event)
        }
    }

    /**
     * Starts a new automation session for [request]. If a session is
     * already active/idle, it is cancelled first — only one session may
     * ever be active. Returns the new session.
     */
    fun start(request: AutomationRequest): AutomationSession {
        activeSession?.let { existing ->
            if (existing.state == SessionState.ACTIVE || existing.state == SessionState.IDLE) {
                existing.state = SessionState.CANCELLED
                ZaraLogger.d("[AutomationEngine] session cancelled (superseded) sessionId=${existing.sessionId}")
            }
        }

        val session = AutomationSession(request = request, state = SessionState.ACTIVE)
        activeSession = session
        ZaraLogger.d(
            "[AutomationEngine] session started sessionId=${session.sessionId} " +
                "targetApp=${request.targetApp} action=${request.action}"
        )
        return session
    }

    /**
     * Stops the active session (marks it CANCELLED) and clears it.
     * No-op if no session is active.
     */
    fun stop() {
        val session = activeSession ?: return
        if (session.state == SessionState.ACTIVE || session.state == SessionState.IDLE) {
            session.state = SessionState.CANCELLED
        }
        ZaraLogger.d("[AutomationEngine] session stopped sessionId=${session.sessionId}")
        activeSession = null
    }

    /**
     * Routes a forwarded AutomationEvent into the active session, if any.
     * Pure routing only — no interpretation, no matching, no automation
     * behavior. That belongs to the Automation Module (later batch).
     *
     * Batch 0.5A: also forwards the event to WorkflowExecutor.onEvent()
     * so its WAIT_FOR_PACKAGE step can be satisfied. Still pure routing —
     * no logic about the event lives here.
     */
    fun handleEvent(event: AutomationEvent) {
        val session = activeSession
        if (session == null || session.state != SessionState.ACTIVE) return
        ZaraLogger.d("[AutomationEngine] event routed sessionId=${session.sessionId} package=${event.packageName}")
        WorkflowExecutor.onEvent(event)
    }

    /** Read-only access to the current session, if any. */
    fun currentSession(): AutomationSession? = activeSession
}
