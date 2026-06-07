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

        // Layer 5.3 / Layer 6: ClarificationManager check (sole clarification authority)
        if (ClarificationManager.hasPending()) {
            // Run resolve — for CONTACT/APP returns rebuilt intent; for CONTEXT sets confirmedContextText
            val resolvedIntent = ClarificationManager.resolve(corrected)

            // Layer 6 CONTEXT confirmation: check if context text was confirmed
            val confirmedText = ClarificationManager.popConfirmedContextText()
            if (confirmedText != null) {
                return runPipelineOnText(confirmedText)
            }

            if (resolvedIntent != null) {
                // CONTACT/APP clarification resolved — execute directly
                return intentRouter.route(resolvedIntent)
            }

            val lower = corrected.trim().lowercase()
            if (lower == "cancel" || lower == "stop" || lower == "never mind" || lower == "nevermind") {
                ClarificationManager.clear()
                return "Okay, cancelled."
            }

            if (ClarificationManager.hasPending()) {
                return "I didn't catch that. Please say the name or number of your choice."
            }
            // Clarification expired or abandoned — fall through to normal pipeline
        }

        val segments = CompoundIntentSplitter.split(corrected)
        if (segments.size == 1) return runPipeline(segments[0])

        val responses = mutableListOf<String>()
        for (segment in segments) {
            try { responses.add(runPipeline(segment)) }
            catch (e: Exception) {
                ZaraLogger.e("Segment failed: ${e.message}")
                responses.add("Couldn't complete one of those actions.")
            }
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
        val classified  = classifier.classify(text)
        val slotted     = SlotExtractor.extract(classified)
        val aliased     = PersonalContactResolver.resolve(slotted)
        val resolved    = entityResolver.resolve(aliased)
        val planned     = AppActionPlanner.plan(resolved)
        val guarded     = ExecutionGuard.guard(planned)
        val result      = intentRouter.route(guarded)
        ContextUpdater.update(guarded, result)
        return result
    }
}
