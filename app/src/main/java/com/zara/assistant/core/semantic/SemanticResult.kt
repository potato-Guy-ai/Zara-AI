package com.zara.assistant.core.semantic

/**
 * Layer 6.6 — Generic MiniLM Semantic Engine: shared result types.
 *
 * SemanticSource tracks which layer produced the final intent, enabling
 * telemetry, routing decisions, and trust-level signaling to downstream
 * consumers (e.g. EntityExtractor may want to know if the intent came from
 * a high-confidence rule or a lower-confidence model inference).
 *
 * SemanticResult is immutable. Downstream consumers (IntentRouter, cloud
 * handler) receive it without coupling to MiniLMManager or EntityExtractor.
 * fallbackRequired = true signals that neither rule engine nor MiniLM could
 * classify the input with sufficient confidence — cloud escalation needed.
 */

enum class SemanticSource {
    RULE_ENGINE,
    MINILM,
    CLOUD
}

enum class SemanticIntent {
    MESSAGING,
    CALL,
    REMINDER,
    MUSIC,
    APP_CONTROL,
    NAVIGATION,
    KNOWLEDGE_QUERY,
    SYSTEM_CONTROL,
    SEARCH,
    UNKNOWN
}

data class SemanticResult(
    val intent: SemanticIntent,
    val confidence: Float,
    val entities: Map<String, String> = emptyMap(),
    val source: SemanticSource,
    val fallbackRequired: Boolean = false
)
