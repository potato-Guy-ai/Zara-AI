package com.zara.assistant.voice

import android.content.Context
import com.zara.assistant.continuation.ContinuationContext
import com.zara.assistant.continuation.ContinuationResolver
import com.zara.assistant.continuation.ContinuationScope
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
import com.zara.assistant.streaming.InteractionEventPublisher
import com.zara.assistant.streaming.PipelineState
import com.zara.assistant.streaming.PipelineStateMachine
import com.zara.assistant.streaming.StablePartialRenderer
import com.zara.assistant.streaming.ZaraInteractionEvent
import com.zara.assistant.workflow.WorkflowEngine
import com.zara.assistant.workflow.WorkflowPlanner
import com.zara.assistant.workflow.WorkflowState
import com.zara.assistant.utils.ZaraLogger
import kotlinx.coroutines.*

/**
 * Layer 6.5C: ContinuationResolver at top of processInput().
 * Layer 6.5E: PipelineStateMachine + InteractionEventPublisher wired into all key transitions.
 *             StablePartialRenderer connected to SttManager partial callbacks.
 *             No execution logic changed. Events are fire-and-forget.
 * Layer 6.5E cleanup: ExecutionStarted/Completed/Failed emitted ONLY from executeIntent().
 *             Removed duplicate publishes from runWorkflow() step loop.
 * Layer 6.5G Phase 2: PartialStt published in both startListeningSession onPartial callbacks.
 * Layer 6.5G Phase 2 fix: FinalStt published in both startListeningSession onResult callbacks.
 * Layer 6.5G bugfix: manual mic path now speaks response via TTS (BUG 1).
 *             processText() no longer publishes FinalStt — transcript UI is voice-only (BUG 3).
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
        PipelineStateMachine.transition(PipelineState.COMPLETED)
    }

    private fun onWakeWordDetected() {
        if (isListening) return
        isListening = true; wakeWordManager.pause()
        ttsManager.speak("Yes?") { startListeningSession() }
    }

    private fun startListeningSession() {
        InteractionEventPublisher.publish(ZaraInteractionEvent.ListeningStarted)
        PipelineStateMachine.transition(PipelineState.LISTENING)
        StablePartialRenderer.reset()
        sttManager.startListening(
            onPartial = { partial ->
                StablePartialRenderer.onPartial(partial)
                InteractionEventPublisher.publish(ZaraInteractionEvent.PartialStt(partial))
            },
            onResult  = { rawText ->
                InteractionEventPublisher.publish(ZaraInteractionEvent.ListeningStopped)
                if (rawText.isBlank()) {
                    isListening = false; wakeWordManager.resume(); return@startListening
                }
                StablePartialRenderer.onFinal(rawText)
                InteractionEventPublisher.publish(
                    ZaraInteractionEvent.FinalStt(rawText)
                )
                scope.launch {
                    val response = processInput(rawText)
                    ttsManager.speak(response) { isListening = false; wakeWordManager.resume() }
                }
            }
        )
    }

    fun startManualListening(onResponse: (String) -> Unit) {
        if (isListening) return
        isListening = true; wakeWordManager.pause()
        startListeningSession(onResponse)
    }

    private fun startListeningSession(onResponse: (String) -> Unit) {
        InteractionEventPublisher.publish(ZaraInteractionEvent.ListeningStarted)
        PipelineStateMachine.transition(PipelineState.LISTENING)
        StablePartialRenderer.reset()
        sttManager.startListening(
            onPartial = { partial ->
                StablePartialRenderer.onPartial(partial)
                InteractionEventPublisher.publish(ZaraInteractionEvent.PartialStt(partial))
            },
            onResult  = { rawText ->
                InteractionEventPublisher.publish(ZaraInteractionEvent.ListeningStopped)
                if (rawText.isBlank()) {
                    isListening = false; wakeWordManager.resume(); onResponse(""); return@startListening
                }
                StablePartialRenderer.onFinal(rawText)
                InteractionEventPublisher.publish(
                    ZaraInteractionEvent.FinalStt(rawText)
                )
                scope.launch {
                    val response = processInput(rawText)
                    isListening = false; wakeWordManager.resume()
                    // BUG 1 fix: manual mic path must speak the reply, same as wakeword path.
                    ttsManager.speak(response)
                    withContext(Dispatchers.Main) { onResponse(response) }
                }
            }
        )
    }

    fun processText(text: String, onResponse: (String) -> Unit) {
        scope.launch {
            // BUG 3 fix: typed commands must never publish to the (voice-only) transcript UI.
            val response = processInput(text)
            withContext(Dispatchers.Main) { onResponse(response) }
        }
    }

    private suspend fun processInput(rawText: String): String {
        val corrected = correctionLayer.correct(rawText)
        val lower = corrected.trim().lowercase()

        PipelineStateMachine.transition(PipelineState.PROCESSING)

        // ── Layer 6.5C: Continuation resolver — FIRST ──────────────────────────
        ContinuationResolver.resolve(corrected) { executeIntent(it) }
            ?.let { return it }

        // ── Cancellation ────────────────────────────────────────────────────────
        val CANCEL_WORDS = setOf("cancel", "stop", "leave it", "never mind", "nevermind")
        if (CANCEL_WORDS.any { lower == it }) {
            ExecutionQueue.getWaiting()?.let { ExecutionQueue.markWaitingCancelled(it.plan.id) }
            ConfirmationManager.clear()
            ExecutionQueue.cancelAll()
            ClarificationManager.clear()
            RecoveryManager.clear()
            ContinuationContext.clearAll()
            ExecutionIntelligenceTelemetry.track("cancel_all")
            PipelineStateMachine.transition(PipelineState.COMPLETED)
            return "Okay, cancelled."
        }

        // ── Clarification check ─────────────────────────────────────────────────
        if (ClarificationManager.hasPending()) {
            PipelineStateMachine.transition(PipelineState.WAITING_CLARIFICATION)
            val resolvedIntent = ClarificationManager.resolve(corrected)
            val confirmedText  = ClarificationManager.popConfirmedContextText()
            if (confirmedText != null) return runPipelineOnText(confirmedText)
            if (resolvedIntent != null) return executeIntent(resolvedIntent)
            if (ClarificationManager.hasPending()) return "I didn't catch that. Please say the name or number."
        }

        // ── Normal pipeline ─────────────────────────────────────────────────────
        val segments = CompoundIntentSplitter.split(corrected)
        if (segments.size == 1) return runPipeline(segments[0])
        return runWorkflow(segments)
    }

    private suspend fun runWorkflow(segments: List<String>): String {
        val intents = mutableListOf<ZaraIntent>()
        for (seg in segments) {
            val intent = try { buildIntent(seg) } catch (e: Exception) {
                ZaraLogger.e("[Workflow] buildIntent failed: ${e.message}")
                continue
            }
            intents.add(intent)
            if (ClarificationManager.hasPending()) {
                PipelineStateMachine.transition(PipelineState.WAITING_CLARIFICATION)
                InteractionEventPublisher.publish(ZaraInteractionEvent.ClarificationRequired(emptyList()))
                return "I need more information. Please clarify."
            }
            if (ConfirmationManager.hasPending()) {
                val req = ConfirmationManager.getPending()
                if (req != null) {
                    ContinuationContext.activate(ContinuationScope.CONFIRMATION)
                    PipelineStateMachine.transition(PipelineState.WAITING_CONFIRMATION)
                    InteractionEventPublisher.publish(ZaraInteractionEvent.ConfirmationRequired(req.prompt))
                    return req.prompt
                }
            }
        }

        if (intents.isEmpty()) return "I couldn't process that."

        val workflowPlan = WorkflowPlanner.plan(intents)
        ExecutionQueue.clearCompleted()
        val (submittedPlan, handle) = WorkflowEngine.submit(workflowPlan)

        if (submittedPlan.state == WorkflowState.FAILED) return "I couldn't prepare that workflow."

        ContinuationContext.activate(ContinuationScope.WORKFLOW)
        PipelineStateMachine.transition(PipelineState.WORKFLOW_RUNNING)
        InteractionEventPublisher.publish(ZaraInteractionEvent.WorkflowStarted(intents.size))

        val responses     = mutableListOf<String>()
        val enqueuedItems = mutableListOf<QueueItem>()
        var stepFailed    = false
        var stepIndex     = 0

        var item = ExecutionQueue.dequeueNext()
        while (item != null) {
            enqueuedItems.add(item)
            val plan = item.plan
            InteractionEventPublisher.publish(ZaraInteractionEvent.WorkflowStepStarted(stepIndex, plan.intent.action))
            try {
                if (plan.requirements.contains(ExecutionRequirement.CONFIRMATION_REQUIRED)) {
                    val prompt = buildConfirmationPrompt(plan.intent)
                    ConfirmationManager.store(ConfirmationRequest(planId = plan.id, prompt = prompt, plan = plan))
                    ExecutionQueue.markWaiting(plan.id)
                    ContinuationContext.activate(ContinuationScope.CONFIRMATION)
                    PipelineStateMachine.transition(PipelineState.WAITING_CONFIRMATION)
                    InteractionEventPublisher.publish(ZaraInteractionEvent.ConfirmationRequired(prompt))
                    responses.add(prompt)
                    break
                }
                // ExecutionStarted/Completed published inside executeIntent() — single source of truth
                val result = executeIntent(plan.intent)
                ExecutionQueue.markCompleted(plan.id)
                ExecutionIntelligenceTelemetry.track("wf_step_completed", plan.id)
                InteractionEventPublisher.publish(ZaraInteractionEvent.WorkflowStepCompleted(stepIndex, result))
                PipelineStateMachine.transition(PipelineState.WORKFLOW_RUNNING)
                responses.add(result)
                stepIndex++
            } catch (e: Exception) {
                ZaraLogger.e("[Workflow] step failed: ${e.message}")
                ExecutionQueue.markFailed(plan.id)
                val rec = FailureRecord(plan.id, plan.intent, e.message ?: "unknown", listOf("retry", "cancel"))
                FailureMemory.record(rec)
                RecoveryManager.recordFailure(rec)
                ContinuationContext.activate(ContinuationScope.RECOVERY)
                PipelineStateMachine.transition(PipelineState.WAITING_RECOVERY)
                // ExecutionFailed published inside executeIntent() — single source of truth
                InteractionEventPublisher.publish(ZaraInteractionEvent.RecoveryRequired)
                ExecutionIntelligenceTelemetry.track("wf_step_failed", plan.id, e.message)
                responses.add("Couldn't complete one of those actions. Say 'retry' to try again.")
                stepFailed = true
                break
            }
            item = ExecutionQueue.dequeueNext()
        }

        if (stepFailed) WorkflowEngine.cancelWorkflowItems(handle)
        if (ExecutionQueue.pendingCount() == 0) ContinuationContext.deactivate(ContinuationScope.WORKFLOW)

        WorkflowEngine.finalize(submittedPlan, enqueuedItems)
        ExecutionIntelligenceTelemetry.track("wf_finalized", submittedPlan.workflowId, "state=${submittedPlan.state}")
        InteractionEventPublisher.publish(ZaraInteractionEvent.WorkflowCompleted(responses))
        PipelineStateMachine.transition(PipelineState.COMPLETED)

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

    private suspend fun buildIntent(text: String): ZaraIntent {
        val classified = classifier.classify(text)
        val slotted    = SlotExtractor.extract(classified)
        val aliased    = PersonalContactResolver.resolve(slotted)

        if (aliased.extra.containsKey("recipient") || aliased.action == "CALL") {
            PipelineStateMachine.transition(PipelineState.RESOLVING_CONTACT)
            InteractionEventPublisher.publish(ZaraInteractionEvent.ContactResolutionStarted(aliased.target ?: ""))
        } else if (aliased.action == "OPEN_APP" || aliased.extra.containsKey("app")) {
            PipelineStateMachine.transition(PipelineState.RESOLVING_APP)
            InteractionEventPublisher.publish(ZaraInteractionEvent.AppResolutionStarted(aliased.extra["app"] ?: aliased.target ?: ""))
        }

        val resolved = entityResolver.resolve(aliased)

        val phone = resolved.extra["phone_number"]
        val pkg   = resolved.extra["app_package"]
        if (phone != null) InteractionEventPublisher.publish(ZaraInteractionEvent.ContactResolutionCompleted(resolved.extra["contact_name"]))
        if (pkg   != null) InteractionEventPublisher.publish(ZaraInteractionEvent.AppResolutionCompleted(pkg))

        if (resolved.extra["needs_clarification"] == "true") {
            val candidates = resolved.extra["entity_candidates"]?.split("|") ?: emptyList()
            PipelineStateMachine.transition(PipelineState.WAITING_CLARIFICATION)
            InteractionEventPublisher.publish(ZaraInteractionEvent.ClarificationRequired(candidates))
        }

        val planned = AppActionPlanner.plan(resolved)
        return ExecutionGuard.guard(planned)
    }

    private suspend fun executeIntent(intent: ZaraIntent): String {
        PipelineStateMachine.transition(PipelineState.EXECUTING)
        InteractionEventPublisher.publish(ZaraInteractionEvent.ExecutionStarted(intent.action))
        return try {
            val result = intentRouter.route(intent)
            ContextUpdater.update(intent, result)
            val appName = intent.extra["app_name"]
            val pkg     = intent.extra["app_package"]
            if (appName != null) TaskRegistry.register(ActiveTask("app", appName, pkg))
            InteractionEventPublisher.publish(ZaraInteractionEvent.ExecutionCompleted(result))
            PipelineStateMachine.transition(PipelineState.COMPLETED)
            result
        } catch (e: Exception) {
            InteractionEventPublisher.publish(ZaraInteractionEvent.ExecutionFailed(e.message ?: "unknown"))
            PipelineStateMachine.transition(PipelineState.FAILED)
            throw e
        }
    }

    private fun buildConfirmationPrompt(intent: ZaraIntent): String {
        val contact = intent.extra["contact_name"] ?: intent.target ?: "unknown"
        val body    = intent.extra["body"] ?: ""
        return when (intent.action) {
            "SEND_WHATSAPP" -> "Message to $contact ready: \"$body\". Send?"
            "SEND_SMS"      -> "SMS to $contact ready: \"$body\". Send?"
            else            -> "Ready to ${intent.action.lowercase()} $contact. Proceed?"
        }
    }
}
