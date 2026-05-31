package com.zara.assistant.voice

import android.content.Context
import com.zara.assistant.core.IntentRouter
import com.zara.assistant.core.LocalIntentClassifier
import kotlinx.coroutines.*

class VoiceSessionManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val sttManager = SttManager(context)
    private val ttsManager = TtsManager(context)
    private val wakeWordManager = WakeWordManager(context)
    private val correctionLayer = SttCorrectionLayer()
    private val intentRouter = IntentRouter(context)
    private val classifier = LocalIntentClassifier()

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
                val corrected = correctionLayer.correct(rawText)
                val intent = classifier.classify(corrected)
                val response = intentRouter.route(intent)
                ttsManager.speak(response) {
                    isListening = false
                    wakeWordManager.resume()
                }
            }
        }
    }

    /**
     * CR-2 fix: entry point for manual STT activation from the UI mic button.
     * Starts listening immediately without going through the wake word or classifier.
     * STT result is corrected and classified exactly as the wake-word path does.
     * onResponse is always invoked on the Main thread.
     */
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
                val corrected = correctionLayer.correct(rawText)
                val intent = classifier.classify(corrected)
                val response = intentRouter.route(intent)
                isListening = false
                wakeWordManager.resume()
                withContext(Dispatchers.Main) { onResponse(response) }
            }
        }
    }

    /**
     * Entry point for typed text input from the UI.
     * onResponse is always invoked on the Main thread.
     */
    fun processText(text: String, onResponse: (String) -> Unit) {
        scope.launch {
            val corrected = correctionLayer.correct(text)
            val intent = classifier.classify(corrected)
            val response = intentRouter.route(intent)
            withContext(Dispatchers.Main) { onResponse(response) }
        }
    }
}
