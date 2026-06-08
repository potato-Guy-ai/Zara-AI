package com.zara.assistant.voice

import android.content.Context
import com.zara.assistant.context.ContextResolver
import com.zara.assistant.context.ContextUpdater
import com.zara.assistant.core.AppActionPlanner
import com.zara.assistant.core.ClarificationManager
import com.zara.assistant.core.CompoundIntentSplitter
import com.zara.assistant.core.EntityResolver
import com.zara.assistant.core.ExecutionGuard
import com.zara.assistant.core.IntentRouter
import com.zara.assistant.core.LocalIntentClassifier
import com.zara.assistant.core.PersonalContactResolver
import com.zara.assistant.core.SlotExtractor
import com.zara.assistant.execution.ConfirmationManager
import com.zara.assistant.execution.ConflictResolver
import com.zara.assistant.execution.DependencyAnalyzer
import com.zara.assistant.execution.ExecutionIntelligenceTelemetry
import com.zara.assistant.execution.ExecutionPlanner
import com.zara.assistant.execution.ExecutionQueue
import com.zara.assistant.execution.ExecutionRequirement
import com.zara.assistant.execution.FailureMemory
import com.zara.assistant.execution.FailureRecord
import com.zara.assistant.execution.Priority
import com.zara.assistant.execution.QueueItem
import com.zara.assistant.execution.RecoveryManager
import com.zara.assistant.execution.TaskRegistry
import com.zara.assistant.execution.TaskState
import com.zara.assistant.utils.ZaraLogger
import kotlinx.coroutines.*

class VoiceSessionManager(private val context: Context) {

    private val scope           = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val sttManager      = SttManager(context)
    private val ttsManager      = TtsManager(context)
    private val wakeWordManager = WakeWordManager(context)
    private val correctionLayer = SttCorrectionLayer()
    private val intentRouter    = IntentRouter(context)
    private val classifier      = LocalIntentClassifier()
    private val entityResolver  = EntityResolver(context)

    var isListening = false
        private set

    fun start() { wakeWordManager.start { onWakeWordDetected() } }

    fun stop() {
        wakeWordManager.stop(); sttManager.stop(); ttsManager.stop()
        isListening = false; scope.cancel()
    }

    private fun onWakeWordDetected() {
        if (isListening) return
        isListening = true; wakeWordManager.pause()
        ttsManager.speak("Yes?") { startListeningSession() }
    }

    private fun startListeningSession() {
        sttManager.startListening { rawText ->
            if (rawText.isBlank()) { isListening = false; wakeWordManager.resume(); return@startListening }
            scope.launch {
                val response = processInput(rawText)
                ttsManager.speak(response) { isListening = false; wakeWordManager.resume() }
            }
        }
    }

    fun startManualListening(onResponse: (String) -> Unit) {
        if (isListening) return
        isListening = true; wakeWordManager.pause()
        startListeningSession(onResponse)
    }

    private fun startListeningSession(onResponse: (String) -> Unit) {
        sttManager.startListening { rawText ->
            if (rawText.isBlank()) {
                isListening = false; wakeWordManager.resume(); onResponse(""); return@startListening
            }
            scope.launch {
                val response = processInput(rawText)
                isListening = false; wakeWordManager.resume()
                withContext(Dispatchers.Main) { onResponse(response) }
            }
        }
    }

    fun processText(text: String, onResponse: (String) -> Unit) {
        scope.launch {
            val response = processInput(text)
            withContext(Dispatchers.Main) { onResponse(response) }
        }
    }

    private suspend fun processInput(rawText: String): String {
        val corrected = correctionLayer.correct(rawText)
        val lower = corrected.trim().lowercase()

        // ── Cancellation ───────────────────────────────────────────────────────
        val CANCEL_WORDS = setOf("cancel", "stop", "leave it", "never mind", "nevermind")
        if (CANCEL_WORDS.any { lower == it }) {
            ConfirmationManager.clear()
            ExecutionQueue.cancelAll()
            ClarificationManager.clear()
            ExecutionIntelligenceTelemetry.track("cancel_all")
            return "Okay, cancelled."
        }

        // ── Recovery (try again) ───────────────────────────────────────────────
        if (RecoveryManager.isResumeCommand(lower)) {
            val failure = RecoveryManager.popForRetry()
            if (failure != null) {
                ExecutionIntelligenceTelemetry.track("recovery_retry", failure.planId)
                return executeIntent(failure.intent)
            }
        }

        // ── Confirmation check ───────────────────────────────────────────────
        if (ConfirmationManager.hasPending()) {
            val confirmed = ConfirmationManager.resolve(corrected)
            return when (confirmed) {
                true  -> {
                    val plan = ConfirmationManager.getPending()?.plan
                    ConfirmationManager.clear()
                    if (plan != null) {
                        ExecutionIntelligenceTelemetry.track("confirmation_yes", plan.id)
                        executeIntent(plan.intent)
                    } else "Okay."
                }
                false -> { ConfirmationManager.clear(); ExecutionIntelligenceTelemetry.track("confirmation_no"); "Okay, cancelled." }
                null  -> "Please say yes or no."
            }
        }

        // ── Clarification check (Layer 5.3 / Layer 6) ───────────────────────
        if (ClarificationManager.hasPending()) {
            val resolvedIntent = ClarificationManager.resolve(corrected)
            val confirmedText  = ClarificationManager.popConfirmedContextText()
            if (confirmedText != null) return runPipelineOnText(confirmedText)
            if (resolvedIntent != null) return intentRouter.route(resolvedIntent)
            if (ClarificationManager.hasPending()) return "I didn't catch that. Please say the name or number."
        }

        // ── Normal pipeline ───────────────────────────────────────────────────
        val segments = CompoundIntentSplitter.split(corrected)
        if (segments.size == 1) return runPipeline(segments[0])

        // Multi-segment: build plans, analyze dependencies, conflict resolve, queue
        val intents = segments.mapNotNull { seg ->
            try { buildIntent(seg) } catch (e: Exception) { null }
        }
        val plans = intents.map { ExecutionPlanner.plan(it) }
        val analyzed  = DependencyAnalyzer.analyze(plans)
        val resolved  = ConflictResolver.resolve(analyzed)
        ExecutionQueue.clearCompleted()
        resolved.forEach { ExecutionQueue.enqueue(QueueItem(it)) }
        ExecutionIntelligenceTelemetry.track("queue_enqueued", detail = "count=${resolved.size}")

        val responses = mutableListOf<String>()
        var item = ExecutionQueue.dequeueNext()
        while (item != null) {
            val plan = item.plan
            try {
                // Confirmation gate
                if (plan.requirements.contains(ExecutionRequirement.CONFIRMATION_REQUIRED)) {
                    ConfirmationManager.store(com.zara.assistant.execution.ConfirmationRequest(
                        planId = plan.id,
                        prompt = buildConfirmationPrompt(plan.intent),
                        plan   = plan
                    ))
                    ExecutionQueue.markWaiting(plan.id)
                    responses.add(buildConfirmationPrompt(plan.intent))
                    break  // pause queue; resume on confirmation
                }
                val result = executeIntent(plan.intent)
                ExecutionQueue.markCompleted(plan.id)
                ExecutionIntelligenceTelemetry.track("task_completed", plan.id)
                responses.add(result)
            } catch (e: Exception) {
                ZaraLogger.e("Queue task failed: ${e.message}")
                ExecutionQueue.markFailed(plan.id)
                FailureMemory.record(FailureRecord(plan.id, plan.intent, e.message ?: "unknown", listOf("try again", "cancel")))
                RecoveryManager.recordFailure(FailureRecord(plan.id, plan.intent, e.message ?: "unknown", listOf("try again")))
                ExecutionIntelligenceTelemetry.track("task_failed", plan.id, e.message)
                responses.add("Couldn't complete one of those actions.")
            }
            item = ExecutionQueue.dequeueNext()
        }
        return responses.joinToString(". ")
    }

    private suspend fun runPipeline(text: String): String {
        return when (val ctxResult = ContextResolver.resolve(text)) {
            is ContextResolver.TextResult.Prompt       -> ctxResult.message
            is ContextResolver.TextResult.ResolvedText -> runPipelineOnText(ctxResult.text)
            is ContextResolver.TextResult.NoContext    -> runPipelineOnText(ctxResult.text)
        }
    }

    private suspend fun runPipelineOnText(text: String): String {
        val intent = buildIntent(text)
        return executeIntent(intent)
    }

    /** Build a fully-pipeline-processed intent from text. */
    private suspend fun buildIntent(text: String): com.zara.assistant.core.ZaraIntent {
        val classified  = classifier.classify(text)
        val slotted     = SlotExtractor.extract(classified)
        val aliased     = PersonalContactResolver.resolve(slotted)
        val resolved    = entityResolver.resolve(aliased)
        val planned     = AppActionPlanner.plan(resolved)
        return ExecutionGuard.guard(planned)
    }

    /** Execute a fully-processed intent and update context. */
    private suspend fun executeIntent(intent: com.zara.assistant.core.ZaraIntent): String {
        val result = intentRouter.route(intent)
        ContextUpdater.update(intent, result)
        // Update TaskRegistry for active task tracking
        val appName = intent.extra[com.zara.assistant.core.IntentExtra.APP_NAME]
        val pkg     = intent.extra[com.zara.assistant.core.IntentExtra.APP_PACKAGE]
        if (appName != null) {
            TaskRegistry.register(com.zara.assistant.execution.ActiveTask("app", appName, pkg))
        }
        return result
    }

    private fun buildConfirmationPrompt(intent: com.zara.assistant.core.ZaraIntent): String {
        val contact = intent.extra[com.zara.assistant.core.IntentExtra.CONTACT_NAME] ?: intent.target ?: "unknown"
        val body    = intent.extra[com.zara.assistant.core.IntentExtra.BODY] ?: ""
        return when (intent.action) {
            "SEND_WHATSAPP" -> "Message to $contact ready: \"$body\". Send?"
            "SEND_SMS"      -> "SMS to $contact ready: \"$body\". Send?"
            else            -> "Ready to ${intent.action.lowercase()} $contact. Proceed?"
        }
    }
}
