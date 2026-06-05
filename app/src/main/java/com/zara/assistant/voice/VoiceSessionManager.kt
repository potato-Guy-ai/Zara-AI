package com.zara.assistant.voice

import android.content.Context
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
                val corrected = correctionLayer.correct(rawText)
                val intent = entityResolver.resolve(SlotExtractor.extract(classifier.classify(corrected)))
                val response = intentRouter.route(intent)
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
                val corrected = correctionLayer.correct(rawText)
                val intent = entityResolver.resolve(SlotExtractor.extract(classifier.classify(corrected)))
                val response = intentRouter.route(intent)
                isListening = false
                wakeWordManager.resume()
                withContext(Dispatchers.Main) { onResponse(response) }
            }
        }
    }

    fun processText(text: String, onResponse: (String) -> Unit) {
        scope.launch {
            val corrected = correctionLayer.correct(text)
            val intent = entityResolver.resolve(SlotExtractor.extract(classifier.classify(corrected)))
            val response = intentRouter.route(intent)
            withContext(Dispatchers.Main) { onResponse(response) }
        }
    }
}
