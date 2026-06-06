package com.zara.assistant.core

/**
 * Layer 4 — IntentGraph model.
 * Represents the structured output of IntentAtomizer.
 * Immutable after creation. No execution logic.
 */

// ── Slot Type System ───────────────────────────────────────────────────────

enum class SlotType {
    CONTACT_SLOT,
    APP_SLOT,
    ACTION_SLOT,
    MESSAGE_SLOT,
    QUERY_SLOT,
    CALL_TARGET_SLOT,
    CONTENT_SLOT
}

data class TypedSlot(
    val type: SlotType,
    val value: String,
    val confidence: Float          // 0.0–1.0
) {
    /** A token belongs to exactly one SlotType. Enforced at construction. */
    init {
        require(value.isNotBlank()) { "Slot value must not be blank" }
        require(confidence in 0f..1f) { "Confidence must be in [0.0, 1.0]" }
    }
}

// ── Atomic Intent ─────────────────────────────────────────────────────────

enum class AtomicIntentType {
    CALL, MESSAGE, OPEN_APP, PLAY_MEDIA, NAVIGATE, SEARCH, SET_TIMER,
    SYSTEM_CONTROL, GREETING, UNKNOWN
}

data class AtomicIntent(
    val type: AtomicIntentType,
    val slots: List<TypedSlot>,        // immutable after construction
    val confidence: Float,
    val rawSegment: String
) {
    /** Convenience: get first slot of a given type, or null. */
    fun slot(slotType: SlotType): TypedSlot? = slots.firstOrNull { it.type == slotType }
}

// ── IntentGraph ────────────────────────────────────────────────────────────

enum class IntentGraphStatus { VALID, NEEDS_CLARIFICATION, REJECTED }

data class IntentGraph(
    val status: IntentGraphStatus,
    val intents: List<AtomicIntent> = emptyList(),
    val candidates: List<AtomicIntent> = emptyList(), // for NEEDS_CLARIFICATION
    val reason: String? = null                         // for REJECTED
) {
    companion object {
        fun valid(intents: List<AtomicIntent>) =
            IntentGraph(status = IntentGraphStatus.VALID, intents = intents)

        fun needsClarification(candidates: List<AtomicIntent>) =
            IntentGraph(status = IntentGraphStatus.NEEDS_CLARIFICATION, candidates = candidates)

        fun rejected(reason: String) =
            IntentGraph(status = IntentGraphStatus.REJECTED, reason = reason)
    }
}
