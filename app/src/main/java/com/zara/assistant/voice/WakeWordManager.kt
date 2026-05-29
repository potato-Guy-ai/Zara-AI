package com.zara.assistant.voice

import android.content.Context
import com.zara.assistant.utils.ZaraLogger

/**
 * Wake word abstraction layer.
 * Only trigger: "Hey Zara"
 *
 * v1: energy-based VAD fallback (no model required to run).
 * Swap to Porcupine or openWakeWord by implementing WakeWordEngine interface.
 */
class WakeWordManager(private val context: Context) {

    private var engine: WakeWordEngine = EnergyVadEngine(context)
    private var paused = false

    fun start(onDetected: () -> Unit) {
        engine.start { keyword ->
            if (!paused && keyword == "hey_zara") {
                ZaraLogger.d("Wake word detected")
                onDetected()
            }
        }
    }

    fun pause() { paused = true }
    fun resume() { paused = false }
    fun stop() { engine.stop() }

    /** Swap engine at runtime (e.g. after Porcupine key is set) */
    fun swapEngine(newEngine: WakeWordEngine, onDetected: () -> Unit) {
        engine.stop()
        engine = newEngine
        engine.start { keyword ->
            if (!paused && keyword == "hey_zara") onDetected()
        }
    }
}

interface WakeWordEngine {
    fun start(onKeyword: (String) -> Unit)
    fun stop()
}

/**
 * Fallback: triggers on loud audio above threshold.
 * Replace with real model ASAP.
 */
class EnergyVadEngine(private val context: Context) : WakeWordEngine {
    private var running = false
    private var thread: Thread? = null

    override fun start(onKeyword: (String) -> Unit) {
        running = true
        // Minimal placeholder — real implementation records audio and checks energy
        ZaraLogger.d("EnergyVAD running (fallback). Integrate Porcupine for production.")
    }

    override fun stop() { running = false; thread?.interrupt() }
}
