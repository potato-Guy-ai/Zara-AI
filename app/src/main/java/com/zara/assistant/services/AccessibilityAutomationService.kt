package com.zara.assistant.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.zara.assistant.automation.core.UiAutomationEngine
import com.zara.assistant.utils.ZaraLogger

/**
 * Accessibility automation service.
 * Used for: screen lock, home, back, global actions.
 * Kept from original architecture.
 *
 * Layer 6.6D Batch 0.1 — service foundation for the future UI Automation
 * Engine (Automation Request -> UiAutomationEngine -> Automation Session
 * -> Automation Module -> AccessibilityAutomationService -> Android UI).
 * This batch only adds event listening + forwarding. No node scanning, no
 * UI tree inspection, no click automation, no state machine — those are
 * later batches. UiAutomationEngine does not exist yet, so events are
 * forwarded through a temporary listener hook that the engine will
 * subscribe to once built.
 *
 * Layer 6.6D Batch 0.2 — UiAutomationEngine now exists; it attaches to
 * the listener hook itself via UiAutomationEngine.attach() once the
 * service connects. This service still does no routing/automation logic.
 *
 * Layer 6.6D Batch 0.3 — service exposes the root node (getRootNode())
 * for NodeScanner to traverse. The service itself does NOT scan — it
 * only hands out the (possibly null) root, same as rootInActiveWindow.
 */
class AccessibilityAutomationService : AccessibilityService() {

    companion object {
        var instance: AccessibilityAutomationService? = null
            private set
    }

    // Layer 6.6D Batch 0.1: temporary hook point. UiAutomationEngine
    // subscribes here (Batch 0.2) — no engine logic lives in this service.
    private var automationEventListener: ((AutomationEvent) -> Unit)? = null

    fun setAutomationEventListener(listener: ((AutomationEvent) -> Unit)?) {
        automationEventListener = listener
    }

    /**
     * Layer 6.6D Batch 0.3: null-safe exposure of the active window's root
     * node. No scanning/traversal here — that's NodeScanner's job.
     */
    fun getRootNode(): AccessibilityNodeInfo? = rootInActiveWindow

    override fun onServiceConnected() {
        instance = this
        serviceInfo = serviceInfo.apply {
            // Layer 6.6D Batch 0.1: also listen for content changes, not just
            // window state changes, so future automation can react to in-app UI updates.
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
        // Layer 6.6D Batch 0.2: engine subscribes to this service's event hook now that it exists.
        UiAutomationEngine.attach()
        ZaraLogger.d("AccessibilityService connected")
    }

    /**
     * Layer 6.6D Batch 0.1: converts the raw AccessibilityEvent into a
     * lightweight AutomationEvent and forwards it to the listener hook.
     * No node traversal, no rootInActiveWindow access, no automation logic —
     * that belongs to UiAutomationEngine / Automation Module in later batches.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString()
        if (packageName.isNullOrBlank()) return

        val automationEvent = AutomationEvent(
            eventType = event.eventType,
            packageName = packageName,
            className = event.className?.toString(),
            timestamp = event.eventTime
        )

        ZaraLogger.d(
            "[Automation] event=${AccessibilityEvent.eventTypeToString(automationEvent.eventType)} " +
                "package=${automationEvent.packageName}"
        )

        automationEventListener?.invoke(automationEvent)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        automationEventListener = null
        instance = null
        super.onDestroy()
    }

    fun performHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun performBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun performLock() = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    fun takeScreenshot() = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
}
