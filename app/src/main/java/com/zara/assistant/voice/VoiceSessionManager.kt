package com.zara.assistant.voice

import android.content.Context
import com.zara.assistant.core.ClarificationManager
import com.zara.assistant.core.EntityResolver
import com.zara.assistant.core.IntentRouter
import com.zara.assistant.core.LocalIntentClassifier
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

    fun start() {
        wakeWordManager.start { onWakeWordDetected() }
    }

    fun stop() {
        wakeWordManager.stop()
        sttManager.stop()
        ttsManager.stop()
        isListening = false
        scope.cancel()
    }

    private fun onWakeWordDetected() {
        if (isListening) return
        isListening = true
        wakeWordManager.pause()
        ttsManager.speak("Yes?") { startListeningSession() }
    }

    private fun startListeningSession() {
        sttManager.startListening { rawText ->
            if (rawText.isBlank()) {
                isListening = false
                wakeWordManager.resume()
                return@startListening
            }
            scope.launch {
                val response = processInput(rawText)
                ttsManager.speak(response) {
                    isListening = false
                    wakeWordManager.resume()
                }
            }
        }
    }

    fun startManualListening(onResponse: (String) -> Unit) {
        if (isListening) return
        isListening = true
        wakeWordManager.pause()
        startListeningSession(onResponse)
    }

    private fun startListeningSession(onResponse: (String) -> Unit) {
        sttManager.startListening { rawText ->
            if (rawText.isBlank()) {
                isListening = false
                wakeWordManager.resume()
                onResponse("")
                return@startListening
            }
            scope.launch {
                val response = processInput(rawText)
                isListening = false
                wakeWordManager.resume()
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

    /**
     * Layer 5.3: Central input processor.
     * 1. If clarification pending — attempt resolution first.
     * 2. If expired or no pending — run normal pipeline.
     */
    private suspend fun processInput(rawText: String): String {
        val corrected = correctionLayer.correct(rawText)

        // Layer 5.3: clarification intercept
        if (ClarificationManager.hasPending()) {
            val clarificationResponse = intentRouter.tryResolveClarification(corrected)
            if (clarificationResponse != null) return clarificationResponse
            // null means expired — fall through to normal pipeline
        }

        // Normal pipeline
        val intent = entityResolver.resolve(SlotExtractor.extract(classifier.classify(corrected)))
        return intentRouter.route(intent)
    }
}
