package com.zara.assistant.actions

import android.content.Context
import com.zara.assistant.core.AppActionPlanner
import com.zara.assistant.core.ClarificationManager
import com.zara.assistant.core.IntentAction
import com.zara.assistant.core.IntentExtra
import com.zara.assistant.core.ZaraIntent
import com.zara.assistant.models.ClarificationEntityType
import com.zara.assistant.models.PendingClarification
import com.zara.assistant.utils.ZaraLogger
import java.util.UUID

class ActionExecutor(private val context: Context) {

    private val appActions   = AppActions(context)
    private val callActions  = CallActions(context)
    private val mediaActions = MediaActions(context)

    suspend fun execute(intent: ZaraIntent): String {
        ZaraLogger.d("Executing: ${intent.action} target=${intent.target}")

        if (intent.extra[IntentExtra.NEEDS_CLARIFICATION] == "true") {
            return handleClarificationNeeded(intent)
        }

        // Layer 5.6: if AppActionPlan present, execute from plan
        val planApp = intent.extra[AppActionPlanner.KEY_APP]
        if (planApp != null) {
            return executePlan(intent, planApp)
        }

        return try {
            when (intent.action) {
                IntentAction.CALL        -> callActions.call(intent.target ?: return "Who should I call?")
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

                IntentAction.OPEN_APP -> {
                    val pkg     = intent.extra[IntentExtra.APP_PACKAGE]
                    val appName = intent.extra[IntentExtra.APP_NAME]
                        ?: intent.extra[IntentExtra.APP]
                        ?: intent.target
                        ?: return "Which app?"
                    if (pkg != null) appActions.launchByPackage(pkg, appName)
                    else appActions.openApp(intent.target ?: return "Which app?")
                }

                IntentAction.OPEN_CAMERA -> appActions.openCamera()
                IntentAction.SET_ALARM   -> appActions.openAlarm()

                IntentAction.SET_TIMER -> {
                    val seconds = intent.extra[IntentExtra.DURATION]?.toLongOrNull()
                    if (seconds != null) appActions.setTimer(seconds) else appActions.openAlarm()
                }

                IntentAction.PLAY_MUSIC -> {
                    val content = intent.extra[IntentExtra.CONTENT] ?: intent.target
                    val pkg     = intent.extra[IntentExtra.APP_PACKAGE]
                    val appName = intent.extra[IntentExtra.APP_NAME] ?: intent.extra[IntentExtra.APP]
                    if (pkg != null) appActions.playMusicByPackage(pkg, appName, content)
                    else appActions.playMusic(content, intent.extra[IntentExtra.APP])
                }

                IntentAction.NAVIGATE_TO -> {
                    val dest    = intent.extra[IntentExtra.QUERY] ?: intent.target ?: return "Where to?"
                    val pkg     = intent.extra[IntentExtra.APP_PACKAGE]
                    val appName = intent.extra[IntentExtra.APP_NAME] ?: intent.extra[IntentExtra.APP]
                    appActions.navigateTo(dest, preferredPackage = pkg, preferredApp = appName)
                }

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

    // ── Layer 5.6: plan-based execution ──────────────────────────────────────
    private suspend fun executePlan(intent: ZaraIntent, app: String): String {
        val action  = intent.extra[AppActionPlanner.KEY_ACTION] ?: return executeFallback(intent)
        val target  = intent.extra[AppActionPlanner.KEY_TARGET]
        val query   = intent.extra[AppActionPlanner.KEY_QUERY]
        val pkg     = intent.extra[IntentExtra.APP_PACKAGE]
        val appName = intent.extra[IntentExtra.APP_NAME] ?: app

        return try {
            when (app) {
                "whatsapp" -> when (action) {
                    AppActionPlanner.ACTION_VOICE_MESSAGE -> appActions.sendWhatsApp(target ?: return "Who?", "")
                    AppActionPlanner.ACTION_VIDEO_CALL    -> appActions.sendWhatsApp(target ?: return "Who?", "")
                    AppActionPlanner.ACTION_AUDIO_CALL    -> appActions.sendWhatsApp(target ?: return "Who?", "")
                    AppActionPlanner.ACTION_MESSAGE       -> appActions.sendWhatsApp(
                        target ?: return "Who?", intent.extra[IntentExtra.BODY] ?: ""
                    )
                    else -> if (pkg != null) appActions.launchByPackage(pkg, appName)
                            else appActions.openApp("whatsapp")
                }
                "youtube" -> when (action) {
                    AppActionPlanner.ACTION_SEARCH,
                    AppActionPlanner.ACTION_PLAY,
                    AppActionPlanner.ACTION_OPEN_VIDEO ->
                        appActions.playMusic(query ?: target, "youtube")
                    else -> if (pkg != null) appActions.launchByPackage(pkg, appName)
                            else appActions.openApp("youtube")
                }
                "phone" -> callActions.call(target ?: return "Who should I call?")
                "music"  -> if (pkg != null) appActions.playMusicByPackage(pkg, appName, query ?: target)
                            else appActions.playMusic(query ?: target, appName)
                else -> executeFallback(intent)
            }
        } catch (e: Exception) {
            ZaraLogger.e("executePlan error: ${e.message}")
            "Something went wrong."
        }
    }

    private fun executeFallback(intent: ZaraIntent): String {
        val pkg     = intent.extra[IntentExtra.APP_PACKAGE]
        val appName = intent.extra[IntentExtra.APP_NAME] ?: intent.extra[IntentExtra.APP] ?: intent.target ?: return "Which app?"
        return if (pkg != null) appActions.launchByPackage(pkg, appName)
               else appActions.openApp(appName)
    }

    // ── Layer 5.3: clarification ───────────────────────────────────────────────────
    private fun handleClarificationNeeded(intent: ZaraIntent): String {
        val rawCandidates = intent.extra[IntentExtra.ENTITY_CANDIDATES]
            ?.split("|")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        if (rawCandidates.isEmpty()) return "I'm not sure who or what you mean."
        val entityType = when (intent.action) {
            IntentAction.CALL, IntentAction.SEND_SMS, IntentAction.SEND_WHATSAPP -> ClarificationEntityType.CONTACT
            else -> ClarificationEntityType.APP
        }
        val clarification = PendingClarification(
            clarificationId = UUID.randomUUID().toString(),
            originalIntent  = intent,
            entityType      = entityType,
            candidates      = rawCandidates.map {
                com.zara.assistant.models.ClarificationCandidate(it, it)
            }
        )
        ClarificationManager.store(clarification)
        val list = rawCandidates.mapIndexed { i, s -> "${i+1}. $s" }.joinToString(", ")
        return "I found multiple matches: $list. Which one did you mean?"
    }
}
