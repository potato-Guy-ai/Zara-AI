package com.zara.assistant.streaming

/**
 * Layer 6.5E — Stable Partial STT Renderer.
 *
 * Reduces UI flicker from rapid partial STT updates.
 * Only publishes a PartialStt event when the new text is meaningfully
 * different from the last published text.
 *
 * Rules (deterministic, no ML, no timers):
 *   1. New text must be longer than last published by >= MIN_DELTA chars, OR
 *   2. New text has a different last word than last published.
 *
 * No state except last published string.
 */
object StablePartialRenderer {

    private const val MIN_DELTA = 4

    @Volatile
    private var lastPublished: String = ""

    /**
     * Called on every raw partial STT update.
     * Publishes PartialStt event only when meaningful change detected.
     */
    fun onPartial(text: String) {
        if (shouldPublish(text)) {
            lastPublished = text
            InteractionEventPublisher.publish(ZaraInteractionEvent.PartialStt(text))
        }
    }

    /** Called when STT produces a final result. Always publishes. Resets state. */
    fun onFinal(text: String) {
        lastPublished = ""
        InteractionEventPublisher.publish(ZaraInteractionEvent.FinalStt(text))
    }

    /** Reset renderer between sessions. */
    fun reset() { lastPublished = "" }

    private fun shouldPublish(text: String): Boolean {
        if (text.isBlank()) return false
        if (lastPublished.isBlank()) return true
        val lengthGain = text.length - lastPublished.length
        if (lengthGain >= MIN_DELTA) return true
        val newLastWord = text.trim().substringAfterLast(" ")
        val oldLastWord = lastPublished.trim().substringAfterLast(" ")
        return newLastWord != oldLastWord
    }
}
