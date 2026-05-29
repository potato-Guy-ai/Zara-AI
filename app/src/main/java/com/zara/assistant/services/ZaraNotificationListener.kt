package com.zara.assistant.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.zara.assistant.utils.ZaraLogger

/**
 * Reads incoming notifications.
 * Kept from original architecture.
 */
class ZaraNotificationListener : NotificationListenerService() {

    companion object {
        var lastNotification: String? = null
            private set
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        lastNotification = "$title: $text"
        ZaraLogger.d("Notification: $lastNotification")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}
}
