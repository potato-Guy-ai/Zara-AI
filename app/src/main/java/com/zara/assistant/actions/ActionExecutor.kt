package com.zara.assistant.actions

import android.content.Context
import com.zara.assistant.core.IntentAction
import com.zara.assistant.core.ZaraIntent
import com.zara.assistant.utils.ZaraLogger

class ActionExecutor(private val context: Context) {

    private val appActions  = AppActions(context)
    private val callActions = CallActions(context)
    private val mediaActions = MediaActions(context)

    suspend fun execute(intent: ZaraIntent): String {
        ZaraLogger.d("Executing: ${intent.action} target=${intent.target}")
        return try {
            when (intent.action) {
                IntentAction.CALL         -> callActions.call(intent.target ?: return "Who should I call?")
                IntentAction.ANSWER_CALL  -> callActions.answerCall()
                IntentAction.END_CALL     -> callActions.endCall()

                IntentAction.OPEN_APP     -> appActions.openApp(intent.target ?: return "Which app?")
                IntentAction.SEND_SMS     -> appActions.sendSms(
                    intent.target ?: return "Who should I message?",
                    intent.extra["body"] ?: ""
                )
                IntentAction.OPEN_CAMERA  -> appActions.openCamera()
                IntentAction.SET_ALARM    -> appActions.openAlarm()
                IntentAction.SET_TIMER    -> appActions.openAlarm()
                IntentAction.NAVIGATE_TO  -> appActions.navigateTo(intent.target ?: return "Where to?")
                IntentAction.PLAY_MUSIC   -> appActions.playMusic(intent.target)

                IntentAction.SET_WIFI        -> mediaActions.setWifi(intent.extra["on"] == "true")
                IntentAction.SET_BLUETOOTH   -> mediaActions.setBluetooth(intent.extra["on"] == "true")
                IntentAction.SET_FLASHLIGHT  -> mediaActions.setFlashlight(intent.extra["on"] == "true")
                IntentAction.SET_VOLUME      -> mediaActions.adjustVolume(intent.extra["dir"] ?: "up")
                IntentAction.SET_SILENT      -> mediaActions.setSilentMode(intent.extra["on"] == "true")
                IntentAction.LOCK_SCREEN     -> mediaActions.lockScreen()

                else -> "I don't know how to do '${intent.action}' yet."
            }
        } catch (e: Exception) {
            ZaraLogger.e("ActionExecutor error: ${e.message}")
            "Something went wrong executing that."
        }
    }
}
