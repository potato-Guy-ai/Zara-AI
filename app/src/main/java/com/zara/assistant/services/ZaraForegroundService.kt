package com.zara.assistant.services

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.zara.assistant.voice.VoiceSessionManager

/**
 * Foreground service that keeps Zara alive.
 * Owns VoiceSessionManager lifecycle.
 */
class ZaraForegroundService : Service() {

    private lateinit var voiceSession: VoiceSessionManager
    private val CHANNEL_ID = "zara_foreground"
    private val NOTIF_ID = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        voiceSession = VoiceSessionManager(this)
        voiceSession.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        voiceSession.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zara is active")
            .setContentText("Say \"Hey Zara\" to activate")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Zara Assistant", NotificationManager.IMPORTANCE_MIN
        ).apply { description = "Keeps Zara listening in background" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
