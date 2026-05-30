package com.zara.assistant.voice

import android.content.Context
import com.zara.assistant.core.IntentRouter
import com.zara.assistant.core.LocalIntentClassifier
import kotlinx.coroutines.*

/**
 * Owns the full voice lifecycle.
 * Also handles typed text input from UI.
 */
class VoiceSessionManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val sttManager = SttManager(context)
    private val ttsManager = TtsManager(context)
    private val wakeWordManager = WakeWordManager(context)
    private val correctionLayer = SttCorrectionLayer()
    private val intentRouter = IntentRouter(context)

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
                val intent = LocalIntentClassifier.classify(corrected)
                val response = intentRouter.route(intent)
                ttsManager.speak(response) {
                    isListening = false
                    wakeWordManager.resume()
                }
            }
        }
    }

    /**
     * Called from UI for typed or programmatic text input.
     * onResponse is always invoked on the Main thread.
     */
    fun processText(text: String, onResponse: (String) -> Unit) {
        scope.launch {
            val corrected = correctionLayer.correct(text)
            val intent = LocalIntentClassifier.classify(corrected)
            val response = intentRouter.route(intent)
            withContext(Dispatchers.Main) { onResponse(response) }
        }
    }
}
