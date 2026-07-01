package com.zara.assistant.core.semantic

import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.6 — MiniLMManager.
 *
 * Manages the full lifecycle of the on-device MiniLM model:
 * lazy loading, thread-safe state tracking, inference, and unloading.
 *
 * DESIGN GOALS:
 * - Lazy: model is never loaded at app start — only when SemanticIntentEngine
 *   first needs it (i.e. rule engine already failed).
 * - Thread-safe: @Volatile isLoaded flag + synchronized(lock) for
 *   load/unload prevents double-load or use-after-unload races.
 * - ONNX-ready: infer() is declared to accept a raw String and return a
 *   SemanticIntent. The internal dispatch is isolated behind a single
 *   private fun inferInternal() that will be replaced with ONNX Runtime
 *   inference once the model asset is added. Nothing outside this class
 *   needs to change at that point.
 * - Battery-safe: unloadModel() releases the model object so GC can
 *   reclaim memory between sessions; callers (SemanticIntentEngine) control
 *   when to unload.
 *
 * PLACEHOLDER INFERENCE (current):
 * Simple keyword-based dispatch that mirrors what a real MiniLM classifier
 * would produce for obvious inputs. Confidence values are intentionally
 * conservative (0.7 for matched, 0.3 for UNKNOWN) to communicate to
 * callers that this is not a high-precision signal.
 *
 * ONNX INTEGRATION (future):
 * Replace inferInternal() body with:
 *   1. tokenize(text) using a WordPiece/BPE tokenizer asset
 *   2. run OrtSession.run(inputTensor)
 *   3. softmax over logit output → top-1 label + confidence score
 * loadModel() body will initialize OrtEnvironment + OrtSession from assets.
 * No other code needs to change.
 */
class MiniLMManager {

    private val lock = Any()

    @Volatile
    private var loaded = false

    /** Opaque model handle — will be an OrtSession in the ONNX integration. */
    private var modelHandle: Any? = null

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Loads the model into memory. No-op if already loaded.
     * Call from a background thread — this will block during ONNX load.
     */
    fun loadModel() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return  // double-checked locking
            ZaraLogger.d("[MiniLM] loading model (placeholder)")
            // TODO: replace with OrtEnvironment.getEnvironment() + OrtSession init
            modelHandle = "placeholder-model-handle"
            loaded = true
            ZaraLogger.d("[MiniLM] model loaded")
        }
    }

    /**
     * Releases the model from memory. Safe to call even if not loaded.
     * After unloading, the next infer() call will trigger a reload via
     * SemanticIntentEngine — or callers may call loadModel() explicitly.
     */
    fun unloadModel() {
        synchronized(lock) {
            if (!loaded) return
            // TODO: (modelHandle as? OrtSession)?.close()
            modelHandle = null
            loaded = false
            ZaraLogger.d("[MiniLM] model unloaded")
        }
    }

    fun isLoaded(): Boolean = loaded

    // ── Inference ─────────────────────────────────────────────────────────

    /**
     * Classifies [text] into a [SemanticIntent].
     * Requires the model to be loaded — callers (SemanticIntentEngine) must
     * call loadModel() first. Returns UNKNOWN with confidence 0.0 as a safe
     * sentinel if called before loading, rather than throwing.
     *
     * Returns a [Pair] of (SemanticIntent, confidence: Float) to allow
     * SemanticIntentEngine to decide whether confidence crosses its
     * threshold for use vs cloud fallback.
     */
    fun infer(text: String): Pair<SemanticIntent, Float> {
        if (!loaded) {
            ZaraLogger.e("[MiniLM] infer() called before loadModel()")
            return Pair(SemanticIntent.UNKNOWN, 0.0f)
        }
        return inferInternal(text.lowercase().trim())
    }

    /**
     * Placeholder inference. Will be replaced with ONNX tokenizer + session
     * run in the ONNX integration milestone. Keyword heuristics here are
     * intentionally coarse — this is not the production classification path.
     */
    private fun inferInternal(t: String): Pair<SemanticIntent, Float> {
        val intent = when {
            t.contains("play") || t.contains("listen") || t.contains("music") ||
            t.contains("song") || t.contains("spotify") || t.contains("album")
                -> SemanticIntent.MUSIC

            t.contains("remind") || t.contains("reminder") || t.contains("in \\d+ hour".toRegex().containsMatchIn(t).toString())
                -> SemanticIntent.REMINDER

            t.contains("call") || t.contains("dial") || t.contains("phone") || t.contains("ring")
                -> SemanticIntent.CALL

            t.contains("message") || t.contains("whatsapp") || t.contains("text") ||
            t.contains("tell") || t.contains("say") || t.contains("inform")
                -> SemanticIntent.MESSAGING

            t.contains("navigate") || t.contains("direction") || t.contains("take me to") ||
            t.contains("drive to") || t.contains("map")
                -> SemanticIntent.NAVIGATION

            t.contains("open") || t.contains("launch") || t.contains("start app")
                -> SemanticIntent.APP_CONTROL

            t.contains("search") || t.contains("find") || t.contains("look up") ||
            t.contains("look for")
                -> SemanticIntent.SEARCH

            t.contains("wifi") || t.contains("bluetooth") || t.contains("flashlight") ||
            t.contains("volume") || t.contains("silent") || t.contains("lock screen") ||
            t.contains("brightness")
                -> SemanticIntent.SYSTEM_CONTROL

            t.contains("what") || t.contains("who") || t.contains("where") ||
            t.contains("when") || t.contains("how") || t.contains("why") ||
            t.contains("explain") || t.contains("tell me about")
                -> SemanticIntent.KNOWLEDGE_QUERY

            else -> SemanticIntent.UNKNOWN
        }

        val confidence = if (intent == SemanticIntent.UNKNOWN) 0.3f else 0.7f
        ZaraLogger.d("[MiniLM] infer result=$intent confidence=$confidence")
        return Pair(intent, confidence)
    }
}
