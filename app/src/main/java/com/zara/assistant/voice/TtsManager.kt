package com.zara.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.zara.assistant.utils.ZaraLogger
import java.util.Locale

/**
 * TTS via Android TTS engine.
 * Optional Piper TTS can be plugged in as a provider later.
 */
class TtsManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val queue = mutableListOf<Pair<String, (() -> Unit)?>>()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                isReady = true
                flushQueue()
            }
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isReady) { queue.add(text to onDone); return }
        val id = System.currentTimeMillis().toString()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onDone(utteranceId: String?) { onDone?.invoke() }
            override fun onError(utteranceId: String?) { onDone?.invoke() }
            override fun onStart(utteranceId: String?) {}
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        ZaraLogger.d("TTS: $text")
    }

    fun stop() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    private fun flushQueue() {
        queue.forEach { (text, cb) -> speak(text, cb) }
        queue.clear()
    }
}
