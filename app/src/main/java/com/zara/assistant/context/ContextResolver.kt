package com.zara.assistant.context

import com.zara.assistant.core.ClarificationManager
import com.zara.assistant.core.IntentExtra
import com.zara.assistant.core.ZaraIntent
import com.zara.assistant.models.ClarificationCandidate
import com.zara.assistant.models.ClarificationEntityType
import com.zara.assistant.models.PendingClarification
import java.util.UUID

/**
 * Layer 6.0 + Critical Architecture Fixes
 *
 * ARCHITECTURE FIX:
 *   pendingContextText REMOVED.
 *   MEDIUM confidence now uses ClarificationManager (sole clarification authority).
 *   PendingClarification with entityType=CONTEXT stores the resolved text as resolvedValue.
 *   ClarificationManager.popConfirmedContextText() returns it after user confirms.
 *   Timeout inherited from PendingClarification.TIMEOUT_MS (30s). No second timeout.
 *
 * ContextResolver operates on TEXT before classification (no pre-classified intent needed).
 * Returns TextResult: ResolvedText | Prompt | NoContext.
 */
object ContextResolver {

    private val PERSON_PRONOUNS = setOf(
        "him", "her", "them", "that person", "that contact",
        "same person", "another message to him", "another message to her"
    )
    private val APP_PRONOUNS = setOf(
        "that app", "same app", "search there", "open it", "continue there"
    )
    private val MEDIA_PRONOUNS = setOf(
        "that song", "that video", "that one", "another one",
        "same song", "same video", "play next one", "play another", "same artist"
    )
    private val UNSAFE_ACTIONS = setOf(
        "delete", "format", "reset", "payment", "pay",
        "transfer", "install", "uninstall", "settings"
    )

    sealed class TextResult {
        /** Resolved text — classify once and run pipeline */
        data class ResolvedText(val text: String) : TextResult()
        /** Return to user; do NOT classify */
        data class Prompt(val message: String) : TextResult()
        /** No pronoun or context unsafe — pass through unchanged */
        data class NoContext(val text: String) : TextResult()
    }

    fun resolve(corrected: String): TextResult {
        val lower = corrected.lowercase().trim()
        if (UNSAFE_ACTIONS.any { lower.contains(it) }) return TextResult.NoContext(corrected)
        if (PERSON_PRONOUNS.any { lower.contains(it) }) return resolvePersonText(lower, corrected)
        if (APP_PRONOUNS.any { lower.contains(it) })    return resolveAppText(lower, corrected)
        if (MEDIA_PRONOUNS.any { lower.contains(it) })  return resolveMediaText(lower, corrected)
        return TextResult.NoContext(corrected)
    }

    private fun resolvePersonText(lower: String, original: String): TextResult {
        val ctx = ConversationContextManager.lastPerson()
            ?: return TextResult.Prompt("I no longer know who you're referring to. Who would you like to contact?")
        if (ctx.confidence == ContextConfidence.LOW) return TextResult.NoContext(original)
        val resolved = replacePronoun(original, PERSON_PRONOUNS, ctx.contactName)
        return if (ctx.confidence == ContextConfidence.HIGH) {
            TextResult.ResolvedText(resolved)
        } else {
            // MEDIUM: delegate to ClarificationManager (sole authority)
            storeMediumClarification(original, resolved, ctx.contactName)
            TextResult.Prompt("Did you mean ${ctx.contactName}? Say yes or no.")
        }
    }

    private fun resolveAppText(lower: String, original: String): TextResult {
        val ctx = ConversationContextManager.lastApp()
            ?: return TextResult.Prompt("I no longer know which app you mean. Which app?")
        if (ctx.confidence == ContextConfidence.LOW) return TextResult.NoContext(original)
        val resolved = replacePronoun(original, APP_PRONOUNS, ctx.appName)
        return if (ctx.confidence == ContextConfidence.HIGH) {
            TextResult.ResolvedText(resolved)
        } else {
            storeMediumClarification(original, resolved, ctx.appName)
            TextResult.Prompt("Did you mean ${ctx.appName}? Say yes or no.")
        }
    }

    private fun resolveMediaText(lower: String, original: String): TextResult {
        val ctx = ConversationContextManager.lastMedia()
            ?: return TextResult.Prompt("I no longer know which media you mean. What would you like to play?")
        if (ctx.confidence == ContextConfidence.LOW) return TextResult.NoContext(original)
        val name = ctx.song ?: ctx.artist ?: ctx.video ?: ctx.playlist
            ?: return TextResult.NoContext(original)
        val resolved = replacePronoun(original, MEDIA_PRONOUNS, name)
        return if (ctx.confidence == ContextConfidence.HIGH) {
            TextResult.ResolvedText(resolved)
        } else {
            storeMediumClarification(original, resolved, name)
            TextResult.Prompt("Did you mean $name? Say yes or no.")
        }
    }

    /**
     * MEDIUM confidence: stores a CONTEXT-type PendingClarification in ClarificationManager.
     * resolvedValue = resolved text to classify if user confirms.
     * Timeout inherited from PendingClarification.TIMEOUT_MS (30s).
     */
    private fun storeMediumClarification(originalText: String, resolvedText: String, entityName: String) {
        // Use a minimal ZaraIntent as placeholder (rawText carries the original input)
        val placeholder = ZaraIntent(
            type      = com.zara.assistant.core.IntentType.UNKNOWN,
            action    = com.zara.assistant.core.IntentAction.UNKNOWN,
            rawText   = originalText
        )
        ClarificationManager.store(
            PendingClarification(
                clarificationId = UUID.randomUUID().toString(),
                originalIntent  = placeholder,
                entityType      = ClarificationEntityType.CONTEXT,
                candidates      = listOf(
                    ClarificationCandidate(
                        displayName   = entityName,
                        resolvedValue = resolvedText,  // text to re-classify on confirmation
                        confidence    = 0.65f
                    )
                )
            )
        )
    }

    private fun replacePronoun(original: String, pronouns: Set<String>, replacement: String): String {
        var result = original
        for (pronoun in pronouns.sortedByDescending { it.length }) {
            val idx = result.lowercase().indexOf(pronoun)
            if (idx >= 0) {
                result = result.substring(0, idx) + replacement + result.substring(idx + pronoun.length)
                break
            }
        }
        return result
    }
}
