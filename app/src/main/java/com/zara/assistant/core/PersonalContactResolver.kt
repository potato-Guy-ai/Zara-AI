package com.zara.assistant.core

import com.zara.assistant.models.ContactAlias

/**
 * Layer 5.4 — Personal Contact Resolver.
 *
 * Runs before EntityResolver in the pipeline.
 * Rewrites extra[RECIPIENT] using known aliases.
 *
 * Rules:
 * - Alias match → replace RECIPIENT with contactName
 * - No match    → pass through unchanged
 * - Never modifies target or rawText
 * - Session-only, in-memory, no persistence
 */
object PersonalContactResolver {

    private val aliases = mutableMapOf<String, ContactAlias>() // key = alias.lowercase()

    /** Register an alias. alias and contactName are trimmed. */
    fun registerAlias(alias: String, contactName: String, confidence: Float = 1.0f) {
        val key = alias.lowercase().trim()
        aliases[key] = ContactAlias(
            alias       = key,
            contactName = contactName.trim(),
            confidence  = confidence
        )
    }

    /** Remove a previously registered alias. */
    fun removeAlias(alias: String) {
        aliases.remove(alias.lowercase().trim())
    }

    /** Clear all aliases. */
    fun clearAll() { aliases.clear() }

    /**
     * Resolve alias in RECIPIENT slot.
     * Returns intent unchanged if no alias found.
     */
    fun resolve(intent: ZaraIntent): ZaraIntent {
        val recipient = intent.extra[IntentExtra.RECIPIENT] ?: return intent
        val key = recipient.lowercase().trim()
        val alias = aliases[key] ?: return intent

        val newExtra = intent.extra.toMutableMap()
        newExtra[IntentExtra.RECIPIENT] = alias.contactName
        return intent.copy(extra = newExtra)
    }
}
