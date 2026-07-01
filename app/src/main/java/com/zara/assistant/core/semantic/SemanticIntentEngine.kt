package com.zara.assistant.core.semantic

import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.6 — SemanticIntentEngine.
 *
 * Main entry point for the semantic fallback layer. Called only when the
 * rule engine (LocalIntentClassifier) produces an UNKNOWN/low-confidence
 * result — never as a first-pass classifier.
 *
 * Pipeline:
 *   1. Ensure MiniLM is loaded (lazy — loads on first call).
 *   2. Run MiniLMManager.infer(text) → SemanticIntent + confidence.
 *   3. If confidence < threshold → mark fallbackRequired = true.
 *   4. Run EntityExtractor.extract(text, intent) → entity slots.
 *   5. Return SemanticResult (immutable).
 *
 * Thread safety: loadModel() is synchronized inside MiniLMManager.
 * resolve() itself is not synchronized because:
 *   - MiniLMManager.infer() is stateless post-load.
 *   - EntityExtractor is a stateless object.
 *   Parallel resolve() calls after load are safe.
 *
 * Confidence threshold (CONFIDENCE_THRESHOLD = 0.6f):
 *   Below this, the intent is treated as unreliable and fallbackRequired
 *   is set to true, signaling the caller (VoiceSessionManager / IntentRouter)
 *   to escalate to cloud. The threshold is a constant here rather than a
 *   constructor parameter to keep the API minimal; promote to a config/
 *   settings value if per-environment tuning is needed later.
 */
class SemanticIntentEngine(
    private val miniLMManager: MiniLMManager
) {

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.6f
    }

    /**
     * Resolves [text] into a [SemanticResult].
     * Always returns a value — never throws. UNKNOWN + fallbackRequired = true
     * is the safe sentinel for any internal failure.
     */
    fun resolve(text: String): SemanticResult {
        return try {
            ensureLoaded()
            val (intent, confidence) = miniLMManager.infer(text)
            val entities = EntityExtractor.extract(text, intent)
            val needsFallback = intent == SemanticIntent.UNKNOWN || confidence < CONFIDENCE_THRESHOLD

            ZaraLogger.d(
                "[SemanticEngine] intent=$intent confidence=$confidence " +
                "entities=${entities.keys} fallback=$needsFallback"
            )

            SemanticResult(
                intent          = intent,
                confidence      = confidence,
                entities        = entities,
                source          = SemanticSource.MINILM,
                fallbackRequired = needsFallback
            )
        } catch (e: Exception) {
            ZaraLogger.e("[SemanticEngine] resolve failed: ${e.message}")
            SemanticResult(
                intent           = SemanticIntent.UNKNOWN,
                confidence       = 0.0f,
                entities         = emptyMap(),
                source           = SemanticSource.MINILM,
                fallbackRequired = true
            )
        }
    }

    /**
     * Loads the model if not already loaded.
     * Called lazily on the first resolve() — never at construction time.
     */
    private fun ensureLoaded() {
        if (!miniLMManager.isLoaded()) {
            ZaraLogger.d("[SemanticEngine] model not loaded — loading now")
            miniLMManager.loadModel()
        }
    }
}
