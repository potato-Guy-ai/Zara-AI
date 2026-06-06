package com.zara.assistant.actions

import android.content.Context
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

        // Layer 5.3: Store clarification and return prompt instead of blocking
        if (intent.extra[IntentExtra.NEEDS_CLARIFICATION] == "true") {
            return handleClarificationNeeded(intent)
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

    // ── Clarification storage (Layer 5.3) ───────────────────────────────────
    private fun handleClarificationNeeded(intent: ZaraIntent): String {
        val rawCandidates = intent.extra[IntentExtra.ENTITY_CANDIDATES]
            ?.split("|")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        if (rawCandidates.isEmpty()) return "I'm not sure who or what you mean."

        // Determine entity type from action
        val entityType = when (intent.action) {
            IntentAction.CALL,
            IntentAction.SEND_SMS,
            IntentAction.SEND_WHATSAPP -> ClarificationEntityType.CONTACT
            else                       -> ClarificationEntityType.APP
        }

        // Build candidates — resolvedValue stored from existing slots if possible
        // For multi-match, EntityResolver stored only display names in ENTITY_CANDIDATES.
        // We store display name as resolvedValue placeholder; EntityResolver can be updated
        // in a future phase to store full pairs. For now: displayName == resolvedValue
        // for contacts (phone lookup deferred to post-clarification resolve).
        // This is safe: CallActions.call() re-resolves by name when no PHONE_NUMBER present.
        val candidatePairs = rawCandidates.map { it to it }

        val clarification = PendingClarification(
            clarificationId = UUID.randomUUID().toString(),
            originalIntent  = intent,
            entityType      = entityType,
            candidates      = candidatePairs.map {
                com.zara.assistant.models.ClarificationCandidate(it.first, it.second)
            }
        )
        ClarificationManager.store(clarification)

        val list = rawCandidates.mapIndexed { i, s -> "${i+1}. $s" }.joinToString(", ")
        return "I found multiple matches: $list. Which one did you mean?"
    }
}
