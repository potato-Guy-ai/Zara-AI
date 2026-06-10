package com.zara.assistant.actions

import android.content.Context
import com.zara.assistant.core.AppActionPlanner
import com.zara.assistant.core.ClarificationManager
import com.zara.assistant.core.ExecutionContract
import com.zara.assistant.core.ExecutionGuard
import com.zara.assistant.core.ExecutionTelemetry
import com.zara.assistant.core.IntentAction
import com.zara.assistant.core.IntentExtra
import com.zara.assistant.core.ZaraIntent
import com.zara.assistant.models.ClarificationCandidate
import com.zara.assistant.models.ClarificationEntityType
import com.zara.assistant.models.PendingClarification
import com.zara.assistant.utils.ZaraLogger
import java.util.UUID

/**
 * B2.2: Added SET_ALARM with hour/minute, SHOW_ALARMS, SHOW_TIMERS, OPEN_CLOCK in executeRaw.
 */
class ActionExecutor(private val context: Context) {

    private val appActions   = AppActions(context)
    private val callActions  = CallActions(context)
    private val mediaActions = MediaActions(context)

    suspend fun execute(intent: ZaraIntent): String {
        ZaraLogger.d("Executing: ${intent.action} target=${intent.target}")
        if (intent.extra["unsupported_command"] == "true") return "Sorry, that command isn\'t supported."
        if (intent.extra[IntentExtra.NEEDS_CLARIFICATION] == "true") return handleClarificationNeeded(intent)

        val contract: ExecutionContract? = ExecutionGuard.readContract(intent)
        if (contract != null) {
            return if (!contract.safe) {
                when (contract.fallbackAction) {
                    "open_app" -> {
                        val pkg  = intent.extra[IntentExtra.APP_PACKAGE]
                        val name = intent.extra[IntentExtra.APP_NAME] ?: contract.app
                        if (pkg != null) appActions.launchByPackage(pkg, name) else appActions.openApp(contract.app)
                    }
                    else -> "I need more information to do that."
                }
            } else {
                val result = executeContract(contract, intent)
                ExecutionTelemetry.record(intent = intent.action, confidence = intent.extra[IntentExtra.ENTITY_CONFIDENCE], selectedApp = contract.app, selectedContact = contract.target, executionResult = result)
                result
            }
        }

        val planApp = intent.extra[AppActionPlanner.KEY_APP]
        if (planApp != null) return executePlan(intent, planApp)

        return try { executeRaw(intent) } catch (e: Exception) { ZaraLogger.e("ActionExecutor error: ${e.message}"); "Something went wrong executing that." }
    }

    private suspend fun executeContract(contract: ExecutionContract, intent: ZaraIntent): String {
        val pkg     = intent.extra[IntentExtra.APP_PACKAGE]
        val appName = intent.extra[IntentExtra.APP_NAME] ?: contract.app
        return try {
            when (contract.app) {
                "whatsapp" -> when (contract.action) {
                    AppActionPlanner.ACTION_VOICE_MESSAGE, AppActionPlanner.ACTION_VIDEO_CALL, AppActionPlanner.ACTION_AUDIO_CALL ->
                        appActions.sendWhatsApp(contract.target ?: return "Who?", "")
                    AppActionPlanner.ACTION_MESSAGE ->
                        appActions.sendWhatsApp(contract.target ?: return "Who?", intent.extra[IntentExtra.BODY] ?: "")
                    else -> if (pkg != null) appActions.launchByPackage(pkg, appName) else appActions.openApp("whatsapp")
                }
                "youtube" -> appActions.playMusic(contract.query ?: contract.target, "youtube")
                "phone"   -> callActions.call(contract.target ?: return "Who should I call?")
                "music"   -> if (pkg != null) appActions.playMusicByPackage(pkg, appName, contract.query ?: contract.target)
                             else appActions.playMusic(contract.query ?: contract.target, appName)
                else -> if (pkg != null) appActions.launchByPackage(pkg, appName) else appActions.openApp(contract.app)
            }
        } catch (e: Exception) { ZaraLogger.e("executeContract: ${e.message}"); "Couldn\'t complete \'${contract.action}\' on ${contract.app}." }
    }

    private suspend fun executePlan(intent: ZaraIntent, app: String): String {
        val action  = intent.extra[AppActionPlanner.KEY_ACTION] ?: return executeFallback(intent)
        val target  = intent.extra[AppActionPlanner.KEY_TARGET]
        val query   = intent.extra[AppActionPlanner.KEY_QUERY]
        val pkg     = intent.extra[IntentExtra.APP_PACKAGE]
        val appName = intent.extra[IntentExtra.APP_NAME] ?: app
        return try {
            when (app) {
                "whatsapp" -> when (action) {
                    AppActionPlanner.ACTION_VOICE_MESSAGE, AppActionPlanner.ACTION_VIDEO_CALL, AppActionPlanner.ACTION_AUDIO_CALL ->
                        appActions.sendWhatsApp(target ?: return "Who?", "")
                    AppActionPlanner.ACTION_MESSAGE ->
                        appActions.sendWhatsApp(target ?: return "Who?", intent.extra[IntentExtra.BODY] ?: "")
                    else -> if (pkg != null) appActions.launchByPackage(pkg, appName) else appActions.openApp("whatsapp")
                }
                "youtube" -> appActions.playMusic(query ?: target, "youtube")
                "phone"   -> callActions.call(target ?: return "Who should I call?")
                "music"   -> if (pkg != null) appActions.playMusicByPackage(pkg, appName, query ?: target)
                             else appActions.playMusic(query ?: target, appName)
                else -> executeFallback(intent)
            }
        } catch (e: Exception) { ZaraLogger.e("executePlan: ${e.message}"); "Something went wrong." }
    }

    private suspend fun executeRaw(intent: ZaraIntent): String {
        return when (intent.action) {
            IntentAction.CALL          -> callActions.call(intent.target ?: return "Who should I call?")
            IntentAction.ANSWER_CALL   -> callActions.answerCall()
            IntentAction.END_CALL      -> callActions.endCall()
            IntentAction.SEND_SMS      -> appActions.sendSms(intent.target ?: return "Who should I message?", intent.extra[IntentExtra.BODY] ?: "")
            IntentAction.SEND_WHATSAPP -> appActions.sendWhatsApp(intent.target ?: return "Who should I WhatsApp?", intent.extra[IntentExtra.BODY] ?: "")
            IntentAction.OPEN_APP -> {
                val pkg  = intent.extra[IntentExtra.APP_PACKAGE]
                val name = intent.extra[IntentExtra.APP_NAME] ?: intent.extra[IntentExtra.APP] ?: intent.target ?: return "Which app?"
                if (pkg != null) appActions.launchByPackage(pkg, name) else appActions.openApp(intent.target ?: return "Which app?")
            }
            IntentAction.OPEN_CAMERA -> appActions.openCamera()
            // B2.2: SET_ALARM uses extracted hour/minute
            IntentAction.SET_ALARM -> {
                val hour   = intent.extra[IntentExtra.ALARM_HOUR]?.toIntOrNull()
                val minute = intent.extra[IntentExtra.ALARM_MINUTE]?.toIntOrNull() ?: 0
                if (hour != null) appActions.setAlarm(hour, minute) else appActions.openAlarm()
            }
            IntentAction.SET_TIMER -> {
                val s = intent.extra[IntentExtra.DURATION]?.toLongOrNull()
                if (s != null) appActions.setTimer(s) else appActions.openAlarm()
            }
            // B2.2: clock utilities
            IntentAction.SHOW_ALARMS -> appActions.openAlarm()
            IntentAction.SHOW_TIMERS -> appActions.showTimers()
            IntentAction.OPEN_CLOCK  -> appActions.openAlarm()
            IntentAction.PLAY_MUSIC  -> {
                val content = intent.extra[IntentExtra.CONTENT] ?: intent.target
                val pkg     = intent.extra[IntentExtra.APP_PACKAGE]
                val appName = intent.extra[IntentExtra.APP_NAME] ?: intent.extra[IntentExtra.APP]
                if (pkg != null) appActions.playMusicByPackage(pkg, appName, content)
                else appActions.playMusic(content, intent.extra[IntentExtra.APP])
            }
            IntentAction.SEARCH_QUERY -> {
                val query = intent.extra[IntentExtra.QUERY] ?: intent.target ?: return "What would you like to search for?"
                val app   = intent.extra[IntentExtra.APP_NAME] ?: intent.extra[IntentExtra.APP]
                appActions.search(query, app)
            }
            IntentAction.NAVIGATE_TO -> {
                val dest = intent.extra[IntentExtra.QUERY] ?: intent.target ?: return "Where to?"
                appActions.navigateTo(dest, intent.extra[IntentExtra.APP_PACKAGE], intent.extra[IntentExtra.APP_NAME] ?: intent.extra[IntentExtra.APP])
            }
            IntentAction.SET_WIFI       -> mediaActions.openWifiSettings()
            IntentAction.SET_BLUETOOTH  -> mediaActions.openBluetoothSettings()
            IntentAction.SET_FLASHLIGHT -> mediaActions.setFlashlight(intent.extra[IntentExtra.ON] == "true")
            IntentAction.SET_VOLUME     -> mediaActions.adjustVolume(intent.extra[IntentExtra.DIRECTION] ?: "up")
            IntentAction.SET_SILENT     -> mediaActions.setSilentMode(intent.extra[IntentExtra.ON] == "true", intent.extra[IntentExtra.MODE] ?: "silent")
            IntentAction.LOCK_SCREEN    -> mediaActions.lockScreen()
            else -> "I don\'t know how to do \'${intent.action}\' yet."
        }
    }

    private fun executeFallback(intent: ZaraIntent): String {
        val pkg  = intent.extra[IntentExtra.APP_PACKAGE]
        val name = intent.extra[IntentExtra.APP_NAME] ?: intent.extra[IntentExtra.APP] ?: intent.target ?: return "Which app?"
        return if (pkg != null) appActions.launchByPackage(pkg, name) else appActions.openApp(name)
    }

    private fun handleClarificationNeeded(intent: ZaraIntent): String {
        val rawCandidates = intent.extra[IntentExtra.ENTITY_CANDIDATES]?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
        if (rawCandidates.isEmpty()) return "I\'m not sure who or what you mean."
        if (!ClarificationManager.hasPending()) {
            val entityType = when (intent.action) {
                IntentAction.CALL, IntentAction.SEND_SMS, IntentAction.SEND_WHATSAPP -> ClarificationEntityType.CONTACT
                else -> ClarificationEntityType.APP
            }
            ClarificationManager.store(PendingClarification(clarificationId = UUID.randomUUID().toString(), originalIntent = intent, entityType = entityType, candidates = rawCandidates.map { ClarificationCandidate(it, it) }))
        }
        val list = rawCandidates.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString(", ")
        return "I found multiple matches: $list. Which one did you mean?"
    }
}
