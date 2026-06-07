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
import com.zara.assistant.core.ZaraIntent
import com.zara.assistant.core.IntentType
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

        // Clarification check
        if (ClarificationManager.hasPending()) {
            val r = intentRouter.tryResolveClarification(corrected)
            if (r != null) return r
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
        val classified = classifier.classify(text)

        // Layer 6.0: Context resolution BEFORE slot extraction
        val contextResult = ContextResolver.resolve(text, classified)
        when (contextResult) {
            is ContextResolver.ContextResult.ExpiredPrompt ->
                return contextResult.message
            is ContextResolver.ContextResult.Resolved -> {
                // Context resolved — skip re-classification, use resolved intent
                val resolved = contextResult.intent
                val slotted  = SlotExtractor.extract(resolved)
                val aliased  = PersonalContactResolver.resolve(slotted)
                val entResolved = entityResolver.resolve(aliased)
                val planned  = AppActionPlanner.plan(entResolved)
                val guarded  = ExecutionGuard.guard(planned)
                val result   = intentRouter.route(guarded)
                ContextUpdater.update(guarded, result)
                return result
            }
            is ContextResolver.ContextResult.NoContext -> { /* fall through to normal pipeline */ }
        }

        // Normal pipeline
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
