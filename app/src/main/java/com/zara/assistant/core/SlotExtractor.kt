package com.zara.assistant.core

/**
 * Layer 4A — Slot Extraction Infrastructure.
 *
 * Stateless, pure, no Android dependencies, no coroutines, no background work.
 *
 * v1: Pass-through. Returns intent unchanged.
 * Future phases will plug feature-specific extractors here.
 */
object SlotExtractor {

    /**
     * Entry point for the slot extraction pipeline stage.
     *
     * @param intent The classified intent from LocalIntentClassifier.
     * @return The same intent, potentially enriched with slot data.
     *         In v1 this is always the original intent unchanged.
     */
    fun extract(intent: ZaraIntent): ZaraIntent {
        // v1: infrastructure only — no extraction logic yet.
        return intent
    }
}
