package com.zara.assistant.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.zara.assistant.continuation.ContinuationContext
import com.zara.assistant.continuation.ContinuationScope
import com.zara.assistant.core.AppActionPlanner
import com.zara.assistant.core.ClarificationManager
import com.zara.assistant.core.ExecutionContract
import com.zara.assistant.core.ExecutionGuard
import com.zara.assistant.core.ExecutionTelemetry
import com.zara.assistant.core.IntentAction
import com.zara.assistant.core.IntentExtra
import com.zara.assistant.core.ZaraIntent
import com.zara.assistant.execution.ConfirmationManager
import com.zara.assistant.execution.ConfirmationRequest
import com.zara.assistant.execution.ExecutionPlan
import com.zara.assistant.execution.ExecutionRequirement
import com.zara.assistant.media.MediaControlAction
import com.zara.assistant.media.MediaControlManager
import com.zara.assistant.memory.MemoryManager
import com.zara.assistant.models.ClarificationCandidate
import com.zara.assistant.models.ClarificationEntityType
import com.zara.assistant.models.PendingClarification
import com.zara.assistant.playback.FreePlaybackEngine
import com.zara.assistant.playback.FreePlaybackResultType
import com.zara.assistant.playback.PlaybackIntentParser
import com.zara.assistant.playback.PlaybackOrchestrator
import com.zara.assistant.playback.PlaybackResolver
import com.zara.assistant.playback.PlaybackRoute
import com.zara.assistant.playback.PremiumPlaybackEngine
import com.zara.assistant.playback.SpotifyApiClientImpl
import com.zara.assistant.playback.SpotifyPlaybackResultType
import com.zara.assistant.playback.UserTierDetector
import com.zara.assistant.tasks.ReminderParser
import com.zara.assistant.tasks.TaskModel
import com.zara.assistant.tasks.TaskRepository
import com.zara.assistant.tasks.TaskSchedule
import com.zara.assistant.utils.ZaraLogger
import java.util.UUID

/**
 * Layer 6.5F Phase 1: Added MEDIA_CONTROL case in executeRaw().
 * Layer 6.6 wiring: PLAY_MUSIC now routes through the playback pipeline
 * (PlaybackIntentParser -> PlaybackResolver -> PlaybackOrchestrator ->
 * PremiumPlaybackEngine / FreePlaybackEngine), falling back to the
 * legacy appActions.playMusic(...) path on any failure or FALLBACK_SEARCH route.
 * Layer 6.6 confirmation gate: single-command path now goes through the same
 * ConfirmationManager flow as the workflow path for SEND_WHATSAPP, SEND_SMS, CALL.
 *
 * Layer 6.6 confirmation-loop fix: the gate previously re-triggered on the
 * "yes" re-entry path, because ConfirmationManager.pop() clears `pending`
 * BEFORE onExecute() re-enters this function — so !hasPending() was true
 * again on re-entry and the gate fired a second time, looping forever.
 * Fix: gate now checks an explicit per-intent approval marker
 * (extra["confirmation_approved"]) that ContinuationResolver stamps onto
 * the intent (via .copy(), since extra is an immutable Map) right before
 * calling onExecute() on the CONFIRM path. hasPending() is no longer part
 * of this condition.
 *
 * Confirmation debt cleanup: the separate caller-side hasPending() check
 * (former "Option A" overwrite guard) has been removed. ConfirmationManager
 * .store() now atomically enforces the "only one pending confirmation
 * globally" invariant itself and returns false if a confirmation is
 * already pending. This call site, and VoiceSessionManager.runWorkflow()'s
 * call site, both now gate on store()'s return value instead of duplicating
 * the hasPending() check beforehand.
 *
 * Phase 3 (Task Memory System): SET_REMINDER handling added to executeRaw().
 * Flow: raw utterance → ReminderParser.parse() → TaskRepository.create() →
 * confirmation response echoed to user.
 * AM/PM clarification: when ReminderParser returns ambiguousAmPm=true,
 * a PendingClarification is stored with entityType=AMPM and the original
 * raw utterance in REMINDER_RAW_TEXT. ClarificationManager already resolves
 * the next utterance ("am"/"pm") and restores the original intent via
 * originalIntent; ActionExecutor re-enters executeSetReminder() with the
 * AMPM_HINT extra populated so ReminderParser can re-parse unambiguously.
 *
 * Phase 4 integration point: after TaskRepository.create(), the scheduler
 * call site is clearly marked with TODO(PHASE_4). ReminderScheduler.scheduleNext(context)
 * should be called there — it does not exist yet and is intentionally absent.
 */
class ActionExecutor(private val context: Context) {

    private val appActions   = AppActions(context)
    private val callActions  = CallActions(context)
    private val mediaActions = MediaActions(context)

    // Phase 3: TaskRepository uses MemoryManager which only needs Context.
    // Constructed lazily to avoid DataStore initialisation cost on every
    // ActionExecutor instantiation — most intents never touch the task system.
    private val taskRepository: TaskRepository by lazy { TaskRepository(MemoryManager(context)) }

    suspend fun execute(intent: ZaraIntent): String {
        ZaraLogger.d("Executing: ${intent.action} target=${intent.target}")
        if (intent.extra["unsupported_command"] == "true") return "Sorry, that command isn't supported."
        if (intent.extra[IntentExtra.NEEDS_CLARIFICATION] == "true") return handleClarificationNeeded(intent)

        // ── Confirmation gate (single-command path) ───────────────────────────
        // Layer 6.6 confirmation-loop fix: gated on the per-intent approval
        // marker, not ConfirmationManager.hasPending() (which pop() already
        // cleared by the time the approved intent re-enters here).
        if (ConfirmationManager.requiresConfirmation(intent.action) && intent.extra["confirmation_approved"] != "true") {

            val plan = ExecutionPlan(
                id           = UUID.randomUUID().toString(),
                intent       = intent,
                requirements = setOf(ExecutionRequirement.CONFIRMATION_REQUIRED)
            )
            val prompt = buildConfirmationPrompt(intent)

            // Confirmation debt cleanup: store() itself now enforces the
            // single-pending invariant atomically and returns false instead
            // of overwriting an existing pending confirmation.
            if (!ConfirmationManager.store(ConfirmationRequest(planId = plan.id, prompt = prompt, plan = plan))) {
                return "Please answer the pending confirmation first. Say yes or no."
            }
            ContinuationContext.activate(ContinuationScope.CONFIRMATION)
            ZaraLogger.d("[Confirmation] gate stored for ${intent.action} planId=${plan.id}")
            return prompt
        }
        // ── End confirmation gate ─────────────────────────────────────────────

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

        return try {
            val raw = executeRaw(intent)
            if (raw.startsWith(CallActions.AMBIGUOUS_PREFIX)) handleAmbiguous(raw, intent) else raw
        } catch (e: Exception) { ZaraLogger.e("ActionExecutor error: ${e.message}"); "Something went wrong executing that." }
    }

    private fun buildConfirmationPrompt(intent: ZaraIntent): String {
        val contact = intent.extra[IntentExtra.CONTACT_NAME] ?: intent.target ?: "contact"
        val body    = intent.extra[IntentExtra.BODY]
        return when (intent.action) {
            IntentAction.SEND_WHATSAPP -> if (!body.isNullOrBlank()) "Message to $contact ready: \"$body\". Send?" else "Send WhatsApp to $contact?"
            IntentAction.SEND_SMS      -> if (!body.isNullOrBlank()) "SMS to $contact ready: \"$body\". Send?" else "Send SMS to $contact?"
            IntentAction.CALL          -> "Call $contact now?"
            else                       -> "Confirm action for $contact?"
        }
    }

    private fun executeResolvedCall(intent: ZaraIntent): String? {
        val phone = intent.extra[IntentExtra.PHONE_NUMBER] ?: return null
        val name  = intent.extra[IntentExtra.CONTACT_NAME] ?: intent.target ?: "contact"
        return callActions.dialNumber(phone, name)
    }

    private fun executeResolvedWhatsApp(intent: ZaraIntent): String? {
        val phone = intent.extra[IntentExtra.PHONE_NUMBER] ?: return null
        val name  = intent.extra[IntentExtra.CONTACT_NAME] ?: intent.target ?: "contact"
        val body  = intent.extra[IntentExtra.BODY] ?: ""
        val cleaned = phone.filter { it.isDigit() }
        return try {
            val uri = Uri.parse("https://wa.me/$cleaned?text=${Uri.encode(body)}")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Opening WhatsApp to message $name."
        } catch (e: Exception) { "Couldn't open WhatsApp." }
    }

    private suspend fun executeResolvedSms(intent: ZaraIntent): String? {
        val phone = intent.extra[IntentExtra.PHONE_NUMBER] ?: return null
        val name  = intent.extra[IntentExtra.CONTACT_NAME] ?: intent.target ?: "contact"
        val body  = intent.extra[IntentExtra.BODY] ?: ""
        return try {
            val uri = Uri.parse("smsto:${phone.filter { it.isDigit() }}")
            val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = uri; putExtra("sms_body", body); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(smsIntent)
            "Opening SMS to $name."
        } catch (e: Exception) { "Couldn't open SMS app." }
    }

    private fun handleAmbiguous(sentinel: String, intent: ZaraIntent): String {
        val parts = sentinel.removePrefix(CallActions.AMBIGUOUS_PREFIX).split("|")
        val candidates = mutableListOf<ClarificationCandidate>()
        var i = 0
        while (i + 1 < parts.size) {
            candidates.add(ClarificationCandidate(displayName = parts[i], resolvedValue = parts[i + 1]))
            i += 2
        }
        if (candidates.isEmpty()) return "I found multiple contacts but couldn't list them."
        if (!ClarificationManager.hasPending()) {
            ClarificationManager.store(
                PendingClarification(
                    clarificationId = UUID.randomUUID().toString(),
                    originalIntent  = intent,
                    entityType      = ClarificationEntityType.CONTACT,
                    candidates      = candidates
                )
            )
        }
        val list = candidates.mapIndexed { idx, c -> "${idx + 1}. ${c.displayName}" }.joinToString(", ")
        return "I found multiple contacts: $list. Which one did you mean?"
    }

    private suspend fun executeContract(contract: ExecutionContract, intent: ZaraIntent): String {
        val pkg     = intent.extra[IntentExtra.APP_PACKAGE]
        val appName = intent.extra[IntentExtra.APP_NAME] ?: contract.app
        return try {
            when (contract.app) {
                "whatsapp" -> when (contract.action) {
                    AppActionPlanner.ACTION_VOICE_MESSAGE, AppActionPlanner.ACTION_VIDEO_CALL, AppActionPlanner.ACTION_AUDIO_CALL ->
                        executeResolvedWhatsApp(intent) ?: appActions.sendWhatsApp(contract.target ?: return "Who?", "")
                    AppActionPlanner.ACTION_MESSAGE ->
                        executeResolvedWhatsApp(intent) ?: appActions.sendWhatsApp(contract.target ?: return "Who?", intent.extra[IntentExtra.BODY] ?: "")
                    else -> if (pkg != null) appActions.launchByPackage(pkg, appName) else appActions.openApp("whatsapp")
                }
                "youtube" -> when (contract.action) {
                    AppActionPlanner.ACTION_SEARCH -> appActions.searchYouTube(contract.query ?: contract.target ?: return "What to search?")
                    else -> appActions.playMusic(contract.query ?: contract.target, "youtube")
                }
                "phone" -> executeResolvedCall(intent) ?: run {
                    val raw = callActions.call(contract.target ?: return "Who should I call?")
                    if (raw.startsWith(CallActions.AMBIGUOUS_PREFIX)) handleAmbiguous(raw, intent) else raw
                }
                "music" -> if (pkg != null) appActions.playMusicByPackage(pkg, appName, contract.query ?: contract.target)
                           else appActions.playMusic(contract.query ?: contract.target, appName)
                else -> if (pkg != null) appActions.launchByPackage(pkg, appName) else appActions.openApp(contract.app)
            }
        } catch (e: Exception) { ZaraLogger.e("executeContract: ${e.message}"); "Couldn't complete '${contract.action}' on ${contract.app}." }
    }

    private suspend fun executePlan(intent: ZaraIntent, app: String): String {
        val action  = intent.extra[AppActionPlanner.KEY_ACTION] ?: return executeFallback(intent)
        val target  = intent.extra[AppActionPlanner.KEY_TARGET]
        val query   = intent.extra[AppActionPlanner.KEY_QUERY]
        val pkg     = intent.extra[IntentExtra.APP_PACKAGE]
        val appName = intent.extra[IntentExtra.APP_NAME] ?: app
        return try {
            val raw = when (app) {
                "whatsapp" -> when (action) {
                    AppActionPlanner.ACTION_VOICE_MESSAGE, AppActionPlanner.ACTION_VIDEO_CALL, AppActionPlanner.ACTION_AUDIO_CALL ->
                        executeResolvedWhatsApp(intent) ?: appActions.sendWhatsApp(target ?: return "Who?", "")
                    AppActionPlanner.ACTION_MESSAGE ->
                        executeResolvedWhatsApp(intent) ?: appActions.sendWhatsApp(target ?: return "Who?", intent.extra[IntentExtra.BODY] ?: "")
                    else -> if (pkg != null) appActions.launchByPackage(pkg, appName) else appActions.openApp("whatsapp")
                }
                "youtube" -> when (action) {
                    AppActionPlanner.ACTION_SEARCH -> appActions.searchYouTube(query ?: target ?: return "What should I search on YouTube?")
                    else -> if (pkg != null) appActions.playMusicByPackage(pkg, appName, query ?: target) else appActions.playMusic(query ?: target, "youtube")
                }
                "phone" -> executeResolvedCall(intent) ?: callActions.call(target ?: return "Who should I call?")
                "music" -> if (pkg != null) appActions.playMusicByPackage(pkg, appName, query ?: target)
                           else appActions.playMusic(query ?: target, appName)
                else -> return executeFallback(intent)
            }
            if (raw.startsWith(CallActions.AMBIGUOUS_PREFIX)) handleAmbiguous(raw, intent) else raw
        } catch (e: Exception) { ZaraLogger.e("executePlan: ${e.message}"); "Something went wrong." }
    }

    /**
     * Layer 6.6 wiring — PLAY_MUSIC pipeline entry point.
     * Tries Playback pipeline; falls back to legacy appActions on any
     * failure, FALLBACK_SEARCH route, or non-success engine result.
     * AUTH_REQUIRED from PremiumPlaybackEngine is returned as-is (never
     * falls back to legacy — that would silently bypass the auth prompt).
     */
    private suspend fun executePlayMusic(intent: ZaraIntent): String {
        ZaraLogger.d("[Playback] entered")
        val content = intent.extra[IntentExtra.CONTENT] ?: intent.target
        val pkg     = intent.extra[IntentExtra.APP_PACKAGE]
        val appName = intent.extra[IntentExtra.APP_NAME] ?: intent.extra[IntentExtra.APP]

        fun legacyPlayMusic(): String {
            ZaraLogger.d("[Playback] fallback triggered")
            return if (pkg != null) appActions.playMusicByPackage(pkg, appName, content)
            else appActions.playMusic(content, intent.extra[IntentExtra.APP])
        }

        return try {
            val playbackIntent = PlaybackIntentParser.parse(content ?: "")
            ZaraLogger.d("[Playback] parser=$playbackIntent")
            val target = PlaybackResolver.resolve(playbackIntent)
            ZaraLogger.d("[Playback] target=$target")
            val tier = UserTierDetector.detect()
            ZaraLogger.d("[Playback] tier=$tier")
            val plan = PlaybackOrchestrator.orchestrate(target, tier)
            ZaraLogger.d("[Playback] route=${plan?.route}")

            if (plan == null) {
                legacyPlayMusic()
            } else when (plan.route) {
                PlaybackRoute.PREMIUM_DIRECT -> {
                    ZaraLogger.d("[Playback] premium engine entered")
                    val client = SpotifyApiClientImpl(context)
                    val result = PremiumPlaybackEngine(client).run(plan)
                    when (result.type) {
                        SpotifyPlaybackResultType.AUTH_REQUIRED -> result.message
                        SpotifyPlaybackResultType.SUCCESS -> result.message
                        else -> legacyPlayMusic()
                    }
                }
                PlaybackRoute.FREE_ASSISTED -> {
                    ZaraLogger.d("[Playback] free engine entered")
                    val result = FreePlaybackEngine.run(context, plan)
                    when (result.type) {
                        FreePlaybackResultType.SUCCESS,
                        FreePlaybackResultType.FALLBACK_USED -> result.message
                        else -> legacyPlayMusic()
                    }
                }
                PlaybackRoute.FALLBACK_SEARCH -> legacyPlayMusic()
            }
        } catch (e: Exception) {
            ZaraLogger.e("[Playback] exception=${e.message}")
            legacyPlayMusic()
        }
    }

    // ── Phase 3: SET_REMINDER ─────────────────────────────────────────────────

    /**
     * Handles SET_REMINDER intent from executeRaw().
     *
     * Two entry modes:
     *
     * 1. First call — no AMPM_HINT in extras:
     *    Reads REMINDER_RAW_TEXT, calls ReminderParser.parse().
     *    - Unambiguous result → creates task, returns confirmation string.
     *    - ambiguousAmPm=true → stores a PendingClarification with
     *      entityType=AMPM and the current intent as originalIntent.
     *      The REMINDER_RAW_TEXT is embedded in the originalIntent's extras
     *      so re-entry gets exactly the same raw text back.
     *      Returns a prompt asking "Did you mean AM or PM?".
     *
     * 2. Re-entry after AM/PM clarification — AMPM_HINT is populated:
     *    ClarificationManager resolves "am"/"pm", stamps AMPM_HINT onto a
     *    copy of the originalIntent, and re-fires executeIntent → ActionExecutor.
     *    This function reads AMPM_HINT and appends " am" or " pm" to the
     *    raw text before re-parsing, which lets ReminderParser resolve the
     *    hour unambiguously.
     *
     * Phase 4 integration point: after TaskRepository.create(), call
     *   ReminderScheduler.scheduleNext(context)
     * This is marked with TODO(PHASE_4) below. ReminderScheduler does not
     * exist yet. Do NOT add a stub — the task is created and persisted
     * correctly; Phase 4 only needs to add the scheduling call here.
     */
    private suspend fun executeSetReminder(intent: ZaraIntent): String {
        val rawText  = intent.extra[IntentExtra.REMINDER_RAW_TEXT] ?: intent.rawText
        val ampmHint = intent.extra[IntentExtra.AMPM_HINT]

        // On AM/PM clarification re-entry, append the hint so the parser
        // resolves without ambiguity. e.g. "remind me at 6" + " am" → "remind me at 6 am"
        val textToParse = if (!ampmHint.isNullOrBlank()) "$rawText $ampmHint" else rawText

        val parsed = ReminderParser.parse(textToParse)
        ZaraLogger.d("[Reminder] parsed schedule=${parsed.schedule} deadline=${parsed.deadlineMs} body=${parsed.body} ambiguous=${parsed.ambiguousAmPm}")

        // AM/PM still ambiguous even after a hint (shouldn't happen, but guard it).
        if (parsed.ambiguousAmPm && ampmHint.isNullOrBlank()) {
            // Store clarification so ClarificationManager routes the next
            // "am"/"pm" utterance back here with AMPM_HINT stamped.
            if (!ClarificationManager.hasPending()) {
                ClarificationManager.store(
                    PendingClarification(
                        clarificationId = UUID.randomUUID().toString(),
                        originalIntent  = intent,   // carries REMINDER_RAW_TEXT in extras
                        entityType      = ClarificationEntityType.AMPM,
                        candidates      = listOf(
                            ClarificationCandidate(displayName = "AM", resolvedValue = "am"),
                            ClarificationCandidate(displayName = "PM", resolvedValue = "pm")
                        )
                    )
                )
            }
            return "Did you mean AM or PM?"
        }

        val body = parsed.body.ifBlank { rawText }

        val task = TaskModel(
            body       = body,
            schedule   = parsed.schedule,
            deadline   = parsed.deadlineMs,
            recurrence = parsed.recurrence
        )

        taskRepository.create(task)
        ZaraLogger.d("[Reminder] created task=${task.id} schedule=${task.schedule} deadline=${task.deadline}")

        // TODO(PHASE_4): call ReminderScheduler.scheduleNext(context) here.
        // ReminderScheduler does not exist yet. This is the exact integration
        // point — no other change to this function will be needed in Phase 4.
        // The task is already persisted; scheduleNext() reads active tasks and
        // arms the next AlarmManager alarm.

        return buildReminderConfirmation(parsed, body)
    }

    /** Builds a human-readable confirmation for the created reminder. */
    private fun buildReminderConfirmation(parsed: com.zara.assistant.tasks.ParsedReminderTime, body: String): String {
        val scheduleDesc = when (val s = parsed.schedule) {
            is TaskSchedule.Exact    -> {
                val fmt = java.text.SimpleDateFormat("h:mm a, MMM d", java.util.Locale.getDefault())
                "at ${fmt.format(java.util.Date(s.triggerMs))}"
            }
            is TaskSchedule.Flexible -> "around ${s.label.replace('_', ' ')}"
            TaskSchedule.Unscheduled -> if (parsed.deadlineMs != null) {
                val fmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                "before ${fmt.format(java.util.Date(parsed.deadlineMs))}"
            } else "when you're ready"
        }
        val recurrenceDesc = parsed.recurrence?.let { " — repeats ${it.type.name.lowercase()}" } ?: ""
        return "Got it. I'll remind you to $body $scheduleDesc$recurrenceDesc."
    }

    // ── End Phase 3 ───────────────────────────────────────────────────────────

    private suspend fun executeRaw(intent: ZaraIntent): String {
        return when (intent.action) {
            IntentAction.CALL -> {
                val phone = intent.extra[IntentExtra.PHONE_NUMBER]
                val name  = intent.extra[IntentExtra.CONTACT_NAME] ?: intent.target ?: "contact"
                if (phone != null) callActions.dialNumber(phone, name)
                else callActions.call(intent.target ?: return "Who should I call?")
            }
            IntentAction.ANSWER_CALL -> callActions.answerCall()
            IntentAction.END_CALL    -> callActions.endCall()
            IntentAction.SEND_WHATSAPP -> {
                executeResolvedWhatsApp(intent)
                    ?: appActions.sendWhatsApp(intent.target ?: return "Who should I WhatsApp?", intent.extra[IntentExtra.BODY] ?: "")
            }
            IntentAction.SEND_SMS -> {
                executeResolvedSms(intent)
                    ?: appActions.sendSms(intent.target ?: return "Who should I message?", intent.extra[IntentExtra.BODY] ?: "")
            }
            // Phase 3: reminder handling
            IntentAction.SET_REMINDER -> executeSetReminder(intent)
            IntentAction.OPEN_APP -> {
                val pkg  = intent.extra[IntentExtra.APP_PACKAGE]
                val name = intent.extra[IntentExtra.APP_NAME] ?: intent.extra[IntentExtra.APP] ?: intent.target ?: return "Which app?"
                if (pkg != null) appActions.launchByPackage(pkg, name) else appActions.openApp(intent.target ?: return "Which app?")
            }
            IntentAction.OPEN_CAMERA -> appActions.openCamera()
            IntentAction.SET_ALARM -> {
                val hour   = intent.extra[IntentExtra.ALARM_HOUR]?.toIntOrNull()
                val minute = intent.extra[IntentExtra.ALARM_MINUTE]?.toIntOrNull() ?: 0
                if (hour != null) appActions.setAlarm(hour, minute) else appActions.openAlarm()
            }
            IntentAction.SET_TIMER -> {
                val s = intent.extra[IntentExtra.DURATION]?.toLongOrNull()
                if (s != null) appActions.setTimer(s) else appActions.openAlarm()
            }
            IntentAction.SHOW_ALARMS -> appActions.openAlarm()
            IntentAction.SHOW_TIMERS -> appActions.showTimers()
            IntentAction.OPEN_CLOCK  -> appActions.openAlarm()
            // Layer 6.6 wiring: PLAY_MUSIC now routes through the playback pipeline.
            IntentAction.PLAY_MUSIC  -> executePlayMusic(intent)
            // Layer 6.5F Phase 1: media transport control
            IntentAction.MEDIA_CONTROL -> {
                val actionName = intent.extra[IntentExtra.MEDIA_ACTION]
                val mediaAction = actionName?.let {
                    try { MediaControlAction.valueOf(it) } catch (e: IllegalArgumentException) { null }
                } ?: return "Unknown media action."
                MediaControlManager.execute(context, mediaAction)
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
            else -> "I don't know how to do '${intent.action}' yet."
        }
    }

    private fun executeFallback(intent: ZaraIntent): String {
        val pkg  = intent.extra[IntentExtra.APP_PACKAGE]
        val name = intent.extra[IntentExtra.APP_NAME] ?: intent.extra[IntentExtra.APP] ?: intent.target ?: return "Which app?"
        return if (pkg != null) appActions.launchByPackage(pkg, name) else appActions.openApp(name)
    }

    private fun handleClarificationNeeded(intent: ZaraIntent): String {
        val rawCandidates = intent.extra[IntentExtra.ENTITY_CANDIDATES]?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
        if (rawCandidates.isEmpty()) return "I'm not sure who or what you mean."
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
