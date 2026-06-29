package com.zara.assistant.core.messaging

/**
 * Layer 6.7 — MessageNLU result model.
 *
 * Parser-only output. MessageNLU never constructs a ZaraIntent — only
 * LocalIntentClassifier does that (via its existing action() helper),
 * using these fields.
 */
data class MessageParseResult(
    val contact: String,
    val body: String?,
    val channel: String,
    val confidence: MessageConfidence
)

/**
 * High  -> contact + body both extracted
 * Medium -> contact only (no body captured)
 * Low   -> contact extracted but ambiguous (e.g. a bare pronoun)
 */
enum class MessageConfidence {
    HIGH,
    MEDIUM,
    LOW
}
