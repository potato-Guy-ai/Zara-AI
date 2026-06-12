package com.zara.assistant.voice

import android.content.Context
import com.zara.assistant.context.ContextResolver
import com.zara.assistant.context.ContextUpdater
import com.zara.assistant.core.AppActionPlanner
import com.zara.assistant.core.ClarificationManager
import com.zara.assistant.core.CompoundIntentSplitter
import com.zara.assistant.core.EntityResolver
import com.zara.assistant.core.ExecutionGuard
import com.zara.assistant.core.IntentExtra
import com.zara.assistant.core.IntentRouter
import com.zara.assistant.core.LocalIntentClassifier
import com.zara.assistant.core.PersonalContactResolver
import com.zara.assistant.core.SlotExtractor
import com.zara.assistant.core.ZaraIntent
import com.zara.assistant.execution.ActiveTask
import com.zara.assistant.execution.ConfirmationManager
import com.zara.assistant.execution.ConfirmationRequest
import com.zara.assistant.execution.ExecutionIntelligenceTelemetry
import com.zara.assistant.execution.ExecutionQueue
import com.zara.assistant.execution.ExecutionRequirement
import com.zara.assistant.execution.FailureMemory
import com.zara.assistant.execution.FailureRecord
import com.zara.assistant.execution.QueueItem
import com.zara.assistant.execution.RecoveryManager
import com.zara.assistant.execution.TaskRegistry
import com.zara.assistant.workflow.WorkflowEngine
import com.zara.assistant.workflow.WorkflowPlanner
import com.zara.assistant.workflow.WorkflowState
import com.zara.assistant.utils.ZaraLogger
import kotlinx.coroutines.*

/**
 * Layer 6.5B: Compound commands route through WorkflowPlanner + WorkflowEngine
 * instead of the old ad-hoc DependencyAnalyzer/ConflictResolver loop.
 * Single-segment commands are unchanged.
 */
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

        // ── Cancellation ─────────────────────────────────────────────────────
        val CANCEL_WORDS = setOf("cancel", "stop", "leave it", "never mind", "nevermind")
        if (CANCEL_WORDS.any { lower == it }) {
            ExecutionQueue.getWaiting()?.let { ExecutionQueue.markWaitingCancelled(it.plan.id) }
            ConfirmationManager.clear()
            ExecutionQueue.cancelAll()
            ClarificationManager.clear()
            ExecutionIntelligenceTelemetry.track("cancel_all")
            return "Okay, cancelled."
        }

        // ── Recovery ──────────────────────────────────────────────────────────
        if (RecoveryManager.isResumeCommand(lower)) {
            val failure = RecoveryManager.popForRetry()
            if (failure != null) {
                ExecutionIntelligenceTelemetry.track("recovery_retry", failure.planId)
                return executeIntent(failure.intent)
            }
        }

        // ── Confirmation check ────────────────────────────────────────────────
        if (ConfirmationManager.hasPending()) {
            val confirmed = ConfirmationManager.resolve(corrected)
            return when (confirmed) {
                true -> {
                    val request = ConfirmationManager.pop()
                    if (request != null) {
                        ExecutionQueue.markWaitingCompleted(request.planId)
                        ExecutionIntelligenceTelemetry.track("confirmation_yes", request.planId)
                        executeIntent(request.plan.intent)
                    } else "Okay."
                }
                false -> {
                    val waiting = ExecutionQueue.getWaiting()
                    if (waiting != null) ExecutionQueue.markWaitingCancelled(waiting.plan.id)
                    ConfirmationManager.clear()
                    ExecutionIntelligenceTelemetry.track("confirmation_no")
                    "Okay, cancelled."
                }
                null -> "Please say yes or no."
            }
        }

        // ── Clarification check ───────────────────────────────────────────────
        if (ClarificationManager.hasPending()) {
            val resolvedIntent = ClarificationManager.resolve(corrected)
            val confirmedText  = ClarificationManager.popConfirmedContextText()
            if (confirmedText != null) return runPipelineOnText(confirmedText)
            if (resolvedIntent != null) return executeIntent(resolvedIntent)
            if (ClarificationManager.hasPending()) return "I didn\'t catch that. Please say the name or number."
        }

        // ── Normal pipeline ───────────────────────────────────────────────────
        val segments = CompoundIntentSplitter.split(corrected)
        if (segments.size == 1) return runPipeline(segments[0])

        // ── Layer 6.5B: Workflow path for compound commands ────────────────
        return runWorkflow(segments)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Layer 6.5B: Workflow execution
    // ────────────────────────────────────────────────────────────────────────

    private suspend fun runWorkflow(segments: List<String>): String {
        // 1. Build intents for each segment through the full pipeline.
        //    Stop early if clarification/confirmation is triggered mid-build.
        val intents = mutableListOf<ZaraIntent>()
        for (seg in segments) {
            val intent = try { buildIntent(seg) } catch (e: Exception) {
                ZaraLogger.e("[Workflow] buildIntent failed for seg=\'$seg\': ${e.message}")
                continue
            }
            intents.add(intent)
            if (ClarificationManager.hasPending()) {
                return "I need more information before I can continue. Please clarify."
            }
            if (ConfirmationManager.hasPending()) {
                val req = ConfirmationManager.getPending()
                if (req != null) return req.prompt
            }
        }

        if (intents.isEmpty()) return "I couldn\'t process that."

        // 2. Plan the workflow (sequential dependencies).
        val workflowPlan = WorkflowPlanner.plan(intents)

        // 3. Submit to WorkflowEngine (validates, guards, enqueues).
        ExecutionQueue.clearCompleted()
        val submittedPlan = WorkflowEngine.submit(workflowPlan)

        if (submittedPlan.state == WorkflowState.FAILED) {
            return "I couldn\'t prepare that workflow."
        }

        // 4. Drain the queue sequentially — identical to existing drain loop.
        val responses = mutableListOf<String>()
        val enqueuedItems = mutableListOf<QueueItem>()

        var item = ExecutionQueue.dequeueNext()
        while (item != null) {
            enqueuedItems.add(item)
            val plan = item.plan
            try {
                if (plan.requirements.contains(ExecutionRequirement.CONFIRMATION_REQUIRED)) {
                    val prompt = buildConfirmationPrompt(plan.intent)
                    ConfirmationManager.store(ConfirmationRequest(planId = plan.id, prompt = prompt, plan = plan))
                    ExecutionQueue.markWaiting(plan.id)
                    responses.add(prompt)
                    break
                }
                val result = executeIntent(plan.intent)
                ExecutionQueue.markCompleted(plan.id)
                ExecutionIntelligenceTelemetry.track("wf_step_completed", plan.id)
                responses.add(result)
            } catch (e: Exception) {
                ZaraLogger.e("[Workflow] step failed: ${e.message}")
                ExecutionQueue.markFailed(plan.id)
                val rec = FailureRecord(plan.id, plan.intent, e.message ?: "unknown", listOf("try again", "cancel"))
                FailureMemory.record(rec)
                RecoveryManager.recordFailure(rec)
                ExecutionIntelligenceTelemetry.track("wf_step_failed", plan.id, e.message)
                responses.add("Couldn\'t complete one of those actions.")
                // STOP_WORKFLOW: default policy — break on first failure
                break
            }
            item = ExecutionQueue.dequeueNext()
        }

        // 5. Finalize workflow state.
        WorkflowEngine.finalize(submittedPlan, enqueuedItems)
        ExecutionIntelligenceTelemetry.track("wf_finalized", submittedPlan.workflowId, "state=${submittedPlan.state}")

        return responses.joinToString(". ")
    }

    // ────────────────────────────────────────────────────────────────────────

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

    private suspend fun buildIntent(text: String): ZaraIntent {
        val classified = classifier.classify(text)
        val slotted    = SlotExtractor.extract(classified)
        val aliased    = PersonalContactResolver.resolve(slotted)
        val resolved   = entityResolver.resolve(aliased)
        val planned    = AppActionPlanner.plan(resolved)
        return ExecutionGuard.guard(planned)
    }

    private suspend fun executeIntent(intent: ZaraIntent): String {
        val result  = intentRouter.route(intent)
        ContextUpdater.update(intent, result)
        val appName = intent.extra[IntentExtra.APP_NAME]
        val pkg     = intent.extra[IntentExtra.APP_PACKAGE]
        if (appName != null) TaskRegistry.register(ActiveTask("app", appName, pkg))
        return result
    }

    private fun buildConfirmationPrompt(intent: ZaraIntent): String {
        val contact = intent.extra[IntentExtra.CONTACT_NAME] ?: intent.target ?: "unknown"
        val body    = intent.extra[IntentExtra.BODY] ?: ""
        return when (intent.action) {
            "SEND_WHATSAPP" -> "Message to $contact ready: \"$body\". Send?"
            "SEND_SMS"      -> "SMS to $contact ready: \"$body\". Send?"
            else            -> "Ready to ${intent.action.lowercase()} $contact. Proceed?"
        }
    }
}
