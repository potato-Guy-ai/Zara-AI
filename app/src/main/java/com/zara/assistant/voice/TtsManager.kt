package com.zara.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.zara.assistant.utils.ZaraLogger
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TTS via Android TTS engine.
 * Optional Piper TTS can be plugged in as a provider later.
 *
 * Layer 6.6 reliability fixes:
 *   BUG 1: null tts guard — callback now fires immediately if tts is null.
 *   BUG 2: onStop() handled — QUEUE_FLUSH cancellations no longer silently drop callback.
 *   SAFETY: AtomicBoolean per-utterance ensures callback fires exactly once across
 *           onDone / onError / onStop / null-tts paths.
 */
class TtsManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val queue = mutableListOf<Pair<String, (() -> Unit)?>>()\

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
        // BUG 1 fix: null tts — invoke callback immediately, never silently drop it.
        if (tts == null) {
            onDone?.invoke()
            return
        }
        if (!isReady) { queue.add(text to onDone); return }

        val id = System.currentTimeMillis().toString()
        // SAFETY: guarantee callback fires exactly once regardless of which terminal
        // event fires (onDone / onError / onStop).
        val fired = AtomicBoolean(false)
        val fireOnce: () -> Unit = {
            if (fired.compareAndSet(false, true)) onDone?.invoke()
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?)  { fireOnce() }
            override fun onError(utteranceId: String?) { fireOnce() }
            // BUG 2 fix: QUEUE_FLUSH cancellations arrive here — treat as completion.
            override fun onStop(utteranceId: String?, interrupted: Boolean) { fireOnce() }
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
