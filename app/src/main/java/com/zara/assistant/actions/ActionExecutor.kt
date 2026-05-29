package com.zara.assistant.actions

import android.content.Context
import com.zara.assistant.core.ZaraIntent
import com.zara.assistant.utils.ZaraLogger

/**
 * Central action dispatcher.
 * Receives structured ZaraIntent, routes to correct action module.
 */
class ActionExecutor(private val context: Context) {

    private val appActions = AppActions(context)
    private val callActions = CallActions(context)
    private val mediaActions = MediaActions(context)

    suspend fun execute(intent: ZaraIntent): String {
        ZaraLogger.d("Executing: ${intent.action} target=${intent.target}")
        return try {
            when (intent.action) {
                // Call actions
                "CALL" -> callActions.call(intent.target ?: return "Who should I call?")
                "ANSWER_CALL" -> callActions.answerCall()
                "END_CALL" -> callActions.endCall()

                // App actions
                "OPEN_APP" -> appActions.openApp(intent.target ?: return "Which app?")
                "SEND_SMS" -> appActions.sendSms(
                    intent.target ?: return "Who should I message?",
                    intent.extra["body"] ?: return "What message?"
                )
                "OPEN_CAMERA" -> appActions.openCamera()
                "SET_ALARM" -> appActions.openAlarm()

                // Media / device actions
                "SET_WIFI" -> mediaActions.setWifi(intent.extra["on"] == "true")
                "SET_BLUETOOTH" -> mediaActions.setBluetooth(intent.extra["on"] == "true")
                "SET_FLASHLIGHT" -> mediaActions.setFlashlight(intent.extra["on"] == "true")
                "SET_VOLUME" -> mediaActions.adjustVolume(intent.extra["dir"] ?: "up")
                "SET_SILENT" -> mediaActions.setSilentMode(intent.extra["on"] == "true")
                "LOCK_SCREEN" -> mediaActions.lockScreen()

                else -> "I don't know how to do '${intent.action}' yet."
            }
        } catch (e: Exception) {
            ZaraLogger.e("ActionExecutor error: ${e.message}")
            "Something went wrong executing that."
        }
    }
}
