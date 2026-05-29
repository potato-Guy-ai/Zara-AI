package com.zara.assistant.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.zara.assistant.utils.ZaraLogger

/**
 * Accessibility automation service.
 * Used for: screen lock, home, back, global actions.
 * Kept from original architecture.
 */
class AccessibilityAutomationService : AccessibilityService() {

    companion object {
        var instance: AccessibilityAutomationService? = null
            private set
    }

    override fun onServiceConnected() {
        instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
        ZaraLogger.d("AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun performHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun performBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun performLock() = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    fun takeScreenshot() = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
}
