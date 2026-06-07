package com.zara.assistant.context

import com.zara.assistant.core.IntentExtra
import com.zara.assistant.core.ZaraIntent

/**
 * Layer 6.0 — Context Resolver.
 *
 * Resolves pronoun/reference tokens in rawText BEFORE classification.
 * Returns a new ZaraIntent with extra slots pre-filled from context,
 * or a string prompt if context expired.
 *
 * Safety: context is NEVER used for destructive actions.
 */
object ContextResolver {

    // Pronouns that reference a person
    private val PERSON_PRONOUNS = setOf(
        "him", "her", "them", "that person", "that contact",
        "same person", "another message to him", "another message to her"
    )
    // Pronouns that reference an app
    private val APP_PRONOUNS = setOf(
        "that app", "same app", "search there", "open it", "continue there"
    )
    // Pronouns that reference media
    private val MEDIA_PRONOUNS = setOf(
        "that song", "that video", "that one", "another one",
        "same song", "same video", "play next one", "play another", "same artist"
    )
    // Destructive actions — context NEVER applied
    private val UNSAFE_ACTIONS = setOf(
        "delete", "format", "reset", "payment", "pay",
        "transfer", "install", "uninstall", "settings"
    )

    /**
     * Result sealed class:
     * - Resolved: enriched ZaraIntent ready for pipeline
     * - ExpiredPrompt: context was found but expired — return string to user
     * - NoContext: no pronoun detected or no stored context — pass through unchanged
     */
    sealed class ContextResult {
        data class Resolved(val intent: ZaraIntent) : ContextResult()
        data class ExpiredPrompt(val message: String) : ContextResult()
        data class NoContext(val originalText: String) : ContextResult()
    }

    /**
     * Attempt to resolve pronouns/references in corrected text.
     * The intent is a pre-classified placeholder with rawText = corrected.
     * Returns ContextResult.
     */
    fun resolve(corrected: String, intent: ZaraIntent): ContextResult {
        val lower = corrected.lowercase().trim()

        // Safety: never use context for destructive actions
        if (UNSAFE_ACTIONS.any { lower.contains(it) }) return ContextResult.NoContext(corrected)

        // Person pronoun
        if (PERSON_PRONOUNS.any { lower.contains(it) }) {
            return resolvePersonContext(lower, intent)
        }

        // App pronoun
        if (APP_PRONOUNS.any { lower.contains(it) }) {
            return resolveAppContext(lower, intent)
        }

        // Media pronoun
        if (MEDIA_PRONOUNS.any { lower.contains(it) }) {
            return resolveMediaContext(lower, intent)
        }

        return ContextResult.NoContext(corrected)
    }

    // ── Person resolution ────────────────────────────────────────────

    private fun resolvePersonContext(lower: String, intent: ZaraIntent): ContextResult {
        val ctx = ConversationContextManager.lastPerson()
            ?: return ContextResult.ExpiredPrompt(
                "I no longer know who you're referring to. Who would you like to contact?"
            )

        if (ctx.confidence == ContextConfidence.LOW) return ContextResult.NoContext(lower)
        if (ctx.confidence == ContextConfidence.MEDIUM) {
            // Return prompt asking for confirmation
            return ContextResult.ExpiredPrompt(
                "Did you mean ${ctx.contactName}? Please confirm."
            )
        }

        // HIGH: auto-resolve
        val newExtra = intent.extra.toMutableMap()
        newExtra[IntentExtra.RECIPIENT]    = ctx.contactName
        newExtra[IntentExtra.CONTACT_NAME] = ctx.contactName
        if (ctx.phoneNumber != null) newExtra[IntentExtra.PHONE_NUMBER] = ctx.phoneNumber
        newExtra[IntentExtra.ENTITY_CONFIDENCE] = "1.0"
        return ContextResult.Resolved(intent.copy(extra = newExtra))
    }

    // ── App resolution ───────────────────────────────────────────────

    private fun resolveAppContext(lower: String, intent: ZaraIntent): ContextResult {
        val ctx = ConversationContextManager.lastApp()
            ?: return ContextResult.ExpiredPrompt(
                "I no longer know which app you mean. Which app?"
            )
        if (ctx.confidence == ContextConfidence.LOW) return ContextResult.NoContext(lower)
        if (ctx.confidence == ContextConfidence.MEDIUM) {
            return ContextResult.ExpiredPrompt("Did you mean ${ctx.appName}? Please confirm.")
        }
        val newExtra = intent.extra.toMutableMap()
        newExtra[IntentExtra.APP] = ctx.appName
        if (ctx.packageName != null) newExtra[IntentExtra.APP_PACKAGE] = ctx.packageName
        newExtra[IntentExtra.APP_NAME] = ctx.appName
        return ContextResult.Resolved(intent.copy(extra = newExtra))
    }

    // ── Media resolution ─────────────────────────────────────────────

    private fun resolveMediaContext(lower: String, intent: ZaraIntent): ContextResult {
        val ctx = ConversationContextManager.lastMedia()
            ?: return ContextResult.ExpiredPrompt(
                "I no longer know which media you mean. What would you like to play?"
            )
        if (ctx.confidence == ContextConfidence.LOW) return ContextResult.NoContext(lower)
        if (ctx.confidence == ContextConfidence.MEDIUM) {
            val name = ctx.song ?: ctx.artist ?: ctx.video ?: "that"
            return ContextResult.ExpiredPrompt("Did you mean $name? Please confirm.")
        }
        val newExtra = intent.extra.toMutableMap()
        if (ctx.song     != null) newExtra[IntentExtra.CONTENT] = ctx.song
        if (ctx.artist   != null) newExtra[IntentExtra.ARTIST]  = ctx.artist
        if (ctx.playlist != null) newExtra[IntentExtra.CONTENT] = ctx.playlist
        if (ctx.video    != null) newExtra[IntentExtra.CONTENT] = ctx.video
        return ContextResult.Resolved(intent.copy(extra = newExtra))
    }
}
