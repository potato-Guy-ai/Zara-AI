package com.zara.assistant.service.automation

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.zara.assistant.utils.ZaraLogger

class ZaraNotificationListener : NotificationListenerService() {

    companion object {
        private val recentNotifications = mutableListOf<ZaraNotification>()

        fun getRecent(limit: Int = 10): List<ZaraNotification> =
            recentNotifications.takeLast(limit)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val pkg = sbn.packageName

        val notif = ZaraNotification(
            app = friendlyName(pkg),
            title = title,
            text = text,
            timestamp = sbn.postTime
        )
        recentNotifications.add(notif)
        if (recentNotifications.size > 100) recentNotifications.removeAt(0)
        ZaraLogger.v("Notification: $pkg | $title")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    private fun friendlyName(pkg: String): String = when {
        pkg.contains("whatsapp") -> "WhatsApp"
        pkg.contains("gmail") -> "Gmail"
        pkg.contains("telegram") -> "Telegram"
        pkg.contains("instagram") -> "Instagram"
        pkg.contains("youtube") -> "YouTube"
        else -> pkg.substringAfterLast(".")
    }
}

data class ZaraNotification(
    val app: String,
    val title: String,
    val text: String,
    val timestamp: Long
)
