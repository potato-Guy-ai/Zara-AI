package com.zara.assistant.actions

import android.content.Context
import com.zara.assistant.core.IntentAction
import com.zara.assistant.core.IntentExtra
import com.zara.assistant.core.ZaraIntent
import com.zara.assistant.utils.ZaraLogger

class ActionExecutor(private val context: Context) {

    private val appActions   = AppActions(context)
    private val callActions  = CallActions(context)
    private val mediaActions = MediaActions(context)

    suspend fun execute(intent: ZaraIntent): String {
        ZaraLogger.d("Executing: ${intent.action} target=${intent.target}")

        // Layer 5.2: Clarification guard — block execution for ambiguous entities
        if (intent.extra[IntentExtra.NEEDS_CLARIFICATION] == "true") {
            val candidates = intent.extra[IntentExtra.ENTITY_CANDIDATES]
                ?.split("|")
                ?.joinToString(", ")
                ?: "unknown"
            return "I found multiple matches: $candidates. Which one did you mean?"
        }

        return try {
            when (intent.action) {
                // ── Contact actions ──────────────────────────────────────────────
                IntentAction.CALL -> callActions.call(intent.target ?: return "Who should I call?")
                IntentAction.ANSWER_CALL -> callActions.answerCall()
                IntentAction.END_CALL    -> callActions.endCall()

                IntentAction.SEND_SMS -> appActions.sendSms(
                    intent.target ?: return "Who should I message?",
                    intent.extra[IntentExtra.BODY] ?: ""
                )
                IntentAction.SEND_WHATSAPP -> appActions.sendWhatsApp(
                    intent.target ?: return "Who should I WhatsApp?",
                    intent.extra[IntentExtra.BODY] ?: ""
                )

                // ── App open: prefer APP_PACKAGE → APP → target ──────────────────
                IntentAction.OPEN_APP -> {
                    val pkg     = intent.extra[IntentExtra.APP_PACKAGE]
                    val appName = intent.extra[IntentExtra.APP_NAME]
                        ?: intent.extra[IntentExtra.APP]
                        ?: intent.target
                        ?: return "Which app?"
                    if (pkg != null) appActions.launchByPackage(pkg, appName)
                    else appActions.openApp(intent.target ?: return "Which app?")
                }

                // ── Camera / alarm ────────────────────────────────────────────────
                IntentAction.OPEN_CAMERA -> appActions.openCamera()
                IntentAction.SET_ALARM   -> appActions.openAlarm()

                // ── Timer ─────────────────────────────────────────────────────────
                IntentAction.SET_TIMER -> {
                    val seconds = intent.extra[IntentExtra.DURATION]?.toLongOrNull()
                    if (seconds != null) appActions.setTimer(seconds) else appActions.openAlarm()
                }

                // ── Music: prefer APP_PACKAGE → APP → target ─────────────────────
                IntentAction.PLAY_MUSIC -> {
                    val content = intent.extra[IntentExtra.CONTENT] ?: intent.target
                    val pkg     = intent.extra[IntentExtra.APP_PACKAGE]
                    val appName = intent.extra[IntentExtra.APP_NAME] ?: intent.extra[IntentExtra.APP]
                    if (pkg != null) appActions.playMusicByPackage(pkg, appName, content)
                    else appActions.playMusic(content, intent.extra[IntentExtra.APP])
                }

                // ── Navigate: prefer APP_PACKAGE → APP ───────────────────────────
                IntentAction.NAVIGATE_TO -> {
                    val dest    = intent.extra[IntentExtra.QUERY] ?: intent.target ?: return "Where to?"
                    val pkg     = intent.extra[IntentExtra.APP_PACKAGE]
                    val appName = intent.extra[IntentExtra.APP_NAME] ?: intent.extra[IntentExtra.APP]
                    appActions.navigateTo(dest, preferredPackage = pkg, preferredApp = appName)
                }

                // ── Media / system ────────────────────────────────────────────────
                IntentAction.SET_WIFI       -> mediaActions.openWifiSettings()
                IntentAction.SET_BLUETOOTH  -> mediaActions.openBluetoothSettings()
                IntentAction.SET_FLASHLIGHT -> mediaActions.setFlashlight(intent.extra[IntentExtra.ON] == "true")
                IntentAction.SET_VOLUME     -> mediaActions.adjustVolume(intent.extra[IntentExtra.DIRECTION] ?: "up")
                IntentAction.SET_SILENT     -> mediaActions.setSilentMode(
                    on   = intent.extra[IntentExtra.ON] == "true",
                    mode = intent.extra[IntentExtra.MODE] ?: "silent"
                )
                IntentAction.LOCK_SCREEN -> mediaActions.lockScreen()

                else -> "I don't know how to do '${intent.action}' yet."
            }
        } catch (e: Exception) {
            ZaraLogger.e("ActionExecutor error: ${e.message}")
            "Something went wrong executing that."
        }
    }
}
