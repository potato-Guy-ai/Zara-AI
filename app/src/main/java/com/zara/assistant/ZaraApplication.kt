package com.zara.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.zara.assistant.service.ZaraCoreService
import com.zara.assistant.data.local.ZaraDatabase
import com.zara.assistant.utils.ZaraLogger

class ZaraApplication : Application() {

    companion object {
        const val CHANNEL_ID_CORE = "zara_core_service"
        const val CHANNEL_ID_ALERTS = "zara_alerts"
        lateinit var instance: ZaraApplication
            private set
    }

    val database: ZaraDatabase by lazy { ZaraDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        ZaraLogger.init()
        createNotificationChannels()
        ZaraCoreService.start(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_CORE,
                    "Zara Active",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Zara is listening for wake word" }
            )

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_ALERTS,
                    "Zara Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }
}
