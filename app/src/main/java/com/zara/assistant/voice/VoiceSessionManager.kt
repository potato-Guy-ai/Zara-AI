package com.zara.assistant.voice

import android.content.Context
import com.zara.assistant.core.IntentRouter
import com.zara.assistant.core.ZaraIntent
import kotlinx.coroutines.*

/**
 * Owns the full voice lifecycle:
 * wake → STT → correction → intent → action → TTS
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
        ttsManager.speak("Yes?") {
            startListeningSession()
        }
    }

    private fun startListeningSession() {
        sttManager.startListening { rawText ->
            scope.launch {
                val corrected = correctionLayer.correct(rawText)
                val intent = com.zara.assistant.core.LocalIntentClassifier.classify(corrected)
                val response = intentRouter.route(intent)
                ttsManager.speak(response) {
                    isListening = false
                    wakeWordManager.resume()
                }
            }
        }
    }

    /** Called from UI for typed input */
    fun processText(text: String, onResponse: (String) -> Unit) {
        scope.launch {
            val corrected = correctionLayer.correct(text)
            val intent = com.zara.assistant.core.LocalIntentClassifier.classify(corrected)
            val response = intentRouter.route(intent)
            onResponse(response)
        }
    }
}
