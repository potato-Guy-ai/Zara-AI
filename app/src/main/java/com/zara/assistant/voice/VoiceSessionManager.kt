package com.zara.assistant.voice

import android.content.Context
import com.zara.assistant.core.AppActionPlanner
import com.zara.assistant.core.ClarificationManager
import com.zara.assistant.core.CompoundIntentSplitter
import com.zara.assistant.core.EntityResolver
import com.zara.assistant.core.IntentRouter
import com.zara.assistant.core.LocalIntentClassifier
import com.zara.assistant.core.PersonalContactResolver
import com.zara.assistant.core.SlotExtractor
import kotlinx.coroutines.*

class VoiceSessionManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
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
        isListening = true
        wakeWordManager.pause()
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

        if (ClarificationManager.hasPending()) {
            val r = intentRouter.tryResolveClarification(corrected)
            if (r != null) return r
        }

        val segments = CompoundIntentSplitter.split(corrected)
        if (segments.size == 1) return runPipeline(segments[0])

        val responses = mutableListOf<String>()
        for (segment in segments) {
            val result = runPipeline(segment)
            responses.add(result)
            if (result.startsWith("I couldn't") || result.startsWith("Something went wrong")) break
        }
        return responses.joinToString(". ")
    }

    private suspend fun runPipeline(text: String): String {
        val classified = classifier.classify(text)
        val slotted    = SlotExtractor.extract(classified)
        val aliased    = PersonalContactResolver.resolve(slotted)
        val resolved   = entityResolver.resolve(aliased)
        val planned    = AppActionPlanner.plan(resolved)   // Layer 5.6
        return intentRouter.route(planned)
    }
}
