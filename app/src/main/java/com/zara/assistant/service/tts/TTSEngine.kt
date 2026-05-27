package com.zara.assistant.service.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.zara.assistant.utils.ZaraLogger
import kotlinx.coroutines.*
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * TTSEngine — Text-to-Speech
 *
 * Primary:  Android built-in TTS (zero setup, always available)
 * Optional: Piper TTS (higher quality, run as local process or via piper-android)
 *           Set USE_PIPER = true and place piper binary + voice model in filesDir
 *
 * Voice: en-US female (Samantha-like via locale + pitch tuning)
 */
class TTSEngine(private val context: Context) {

    companion object {
        private const val USE_PIPER = false  // flip to true once Piper is configured
        private const val SPEECH_RATE = 0.95f
        private const val PITCH = 1.1f       // slightly higher = more feminine tone
    }

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val queue = ArrayDeque<String>()

    init {
        initTTS()
    }

    private fun initTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.apply {
                    val result = setLanguage(Locale.US)
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        ZaraLogger.w("TTS: Language not supported, using default")
                        setLanguage(Locale.getDefault())
                    }
                    setSpeechRate(SPEECH_RATE)
                    setPitch(PITCH)
                }
                isReady = true
                ZaraLogger.d("TTS: Engine ready")
                // Drain any queued speech
                scope.launch { drainQueue() }
            } else {
                ZaraLogger.e("TTS: Initialization failed")
            }
        }
    }

    /**
     * Speak text aloud. Suspends until speech is complete.
     */
    suspend fun speak(text: String) {
        if (text.isBlank()) return
        ZaraLogger.d("TTS speaking: $text")

        if (!isReady) {
            queue.add(text)
            return
        }

        if (USE_PIPER) {
            speakWithPiper(text)
        } else {
            speakWithAndroidTTS(text)
        }
    }

    private suspend fun speakWithAndroidTTS(text: String) = suspendCoroutine { cont ->
        val utteranceId = UUID.randomUUID().toString()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) cont.resume(Unit)
            }
            override fun onError(id: String?) {
                if (id == utteranceId) cont.resume(Unit)
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * Piper TTS stub — implement when piper-android is set up.
     * Command: piper --model en_US-amy-low.onnx --output_raw | aplay -r 22050 -f S16_LE -c 1
     */
    private suspend fun speakWithPiper(text: String) = withContext(Dispatchers.IO) {
        try {
            val piperBin = "${context.filesDir}/piper/piper"
            val modelPath = "${context.filesDir}/piper/en_US-amy-low.onnx"
            val process = ProcessBuilder(piperBin, "--model", modelPath, "--output_stdout")
                .redirectErrorStream(true)
                .start()
            process.outputStream.bufferedWriter().use { it.write(text) }
            process.waitFor()
        } catch (e: Exception) {
            ZaraLogger.e("Piper TTS failed, falling back: ${e.message}")
            speakWithAndroidTTS(text)
        }
    }

    private suspend fun drainQueue() {
        while (queue.isNotEmpty()) {
            speak(queue.removeFirst())
        }
    }

    /** Stop any ongoing speech */
    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        scope.cancel()
        tts?.stop()
        tts?.shutdown()
    }
}
