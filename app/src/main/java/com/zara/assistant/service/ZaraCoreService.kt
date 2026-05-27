package com.zara.assistant.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.zara.assistant.R
import com.zara.assistant.ZaraApplication
import com.zara.assistant.service.wake.WakeWordEngine
import com.zara.assistant.service.stt.SpeechRecognizer
import com.zara.assistant.service.ai.ZaraAIEngine
import com.zara.assistant.service.tts.TTSEngine
import com.zara.assistant.ui.MainActivity
import com.zara.assistant.utils.ZaraLogger
import kotlinx.coroutines.*

class ZaraCoreService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var wakeWordEngine: WakeWordEngine
    private lateinit var sttEngine: SpeechRecognizer
    private lateinit var ttsEngine: TTSEngine
    private lateinit var aiEngine: ZaraAIEngine
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val ACTION_START = "com.zara.START"
        const val ACTION_STOP = "com.zara.STOP"
        const val ACTION_MANUAL_ACTIVATE = "com.zara.MANUAL_ACTIVATE"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ZaraCoreService::class.java).apply {
                action = ACTION_START
            })
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ZaraCoreService::class.java).apply {
                action = ACTION_STOP
            })
        }

        fun manualActivate(context: Context) {
            context.startService(Intent(context, ZaraCoreService::class.java).apply {
                action = ACTION_MANUAL_ACTIVATE
            })
        }
    }

    override fun onCreate() {
        super.onCreate()
        ZaraLogger.d("ZaraCoreService created")
        initEngines()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIF_ID, buildNotification("Listening for 'Hey Zara'…"))
                acquireWakeLock()
                startWakeWordLoop()
            }
            ACTION_STOP -> {
                stopSelf()
            }
            ACTION_MANUAL_ACTIVATE -> {
                scope.launch { onWakeWordDetected() }
            }
        }
        return START_STICKY
    }

    private fun initEngines() {
        wakeWordEngine = WakeWordEngine(this)
        sttEngine = SpeechRecognizer(this)
        ttsEngine = TTSEngine(this)
        aiEngine = ZaraAIEngine(this)
    }

    private fun startWakeWordLoop() {
        scope.launch {
            ZaraLogger.d("Wake word loop started")
            wakeWordEngine.start { detected ->
                if (detected) {
                    launch { onWakeWordDetected() }
                }
            }
        }
    }

    private suspend fun onWakeWordDetected() {
        ZaraLogger.d("Wake word detected!")
        updateNotification("Zara is listening…")
        ttsEngine.speak("Yes?")

        val spokenText = sttEngine.listenOnce() ?: return
        ZaraLogger.d("User said: $spokenText")

        updateNotification("Thinking…")
        val response = aiEngine.process(spokenText)

        ZaraLogger.d("Zara response: $response.action")
        ttsEngine.speak(response.speech)
        response.action?.invoke()

        updateNotification("Listening for 'Hey Zara'…")
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Zara::CoreWakeLock"
        ).also { it.acquire() }
    }

    private fun buildNotification(text: String): Notification {
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, ZaraApplication.CHANNEL_ID_CORE)
            .setContentTitle("Zara")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(intent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        wakeWordEngine.stop()
        sttEngine.stop()
        ttsEngine.shutdown()
        wakeLock?.release()
        ZaraLogger.d("ZaraCoreService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
