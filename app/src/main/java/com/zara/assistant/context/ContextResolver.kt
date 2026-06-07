package com.zara.assistant.context

import com.zara.assistant.core.IntentExtra
import com.zara.assistant.core.ZaraIntent
import com.zara.assistant.models.ClarificationCandidate
import com.zara.assistant.models.ClarificationEntityType
import com.zara.assistant.models.PendingClarification
import java.util.UUID

/**
 * Layer 6.0 + Critical Fixes
 *
 * FIX 1: ContextResolver now operates on raw corrected TEXT before classification.
 *   - Input:  corrected string (e.g. "call him")
 *   - Output: TextResult
 *     - ResolvedText: rewritten string (e.g. "call Abdul Rahman") → classify once
 *     - Prompt:       return message to user, do NOT classify
 *     - NoContext:    pass original text through unchanged
 *
 * FIX 2: MEDIUM confidence stores a PendingClarification via ClarificationManager.
 *   "yes" → resolves, runs original rewritten text.
 *   "no"  → clears clarification, normal pipeline.
 *
 * Safety: context NEVER used for destructive actions.
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

    // Yes/no confirmation words for MEDIUM clarification
    private val YES_WORDS = setOf("yes", "yeah", "yep", "sure", "ok", "okay", "correct", "right")
    private val NO_WORDS  = setOf("no", "nope", "cancel", "stop", "never mind", "nevermind")

    // Pending context confirmation (MEDIUM fix 2)
    // Stores: resolved text to execute if user says yes
    private var pendingContextText: String? = null

    sealed class TextResult {
        /** Use this rewritten text for classification — single pass */
        data class ResolvedText(val text: String) : TextResult()
        /** Return this message to user; do NOT classify */
        data class Prompt(val message: String) : TextResult()
        /** No pronoun detected or context unsafe — pass through original */
        data class NoContext(val text: String) : TextResult()
    }

    /**
     * FIX 1: Operates on text BEFORE classification.
     * Returns TextResult. Caller classifies only if ResolvedText or NoContext.
     */
    fun resolve(corrected: String): TextResult {
        val lower = corrected.lowercase().trim()

        // Check for pending MEDIUM context confirmation (FIX 2)
        if (pendingContextText != null) {
            return when {
                YES_WORDS.any { lower == it || lower.startsWith("$it ") } -> {
                    val resolved = pendingContextText!!
                    pendingContextText = null
                    TextResult.ResolvedText(resolved)
                }
                NO_WORDS.any { lower == it || lower.startsWith("$it ") } -> {
                    pendingContextText = null
                    TextResult.NoContext(corrected)
                }
                else -> {
                    // Not a confirmation — abandon pending and continue normally
                    pendingContextText = null
                    resolveFromContext(lower, corrected)
                }
            }
        }

        return resolveFromContext(lower, corrected)
    }

    private fun resolveFromContext(lower: String, original: String): TextResult {
        if (UNSAFE_ACTIONS.any { lower.contains(it) }) return TextResult.NoContext(original)

        if (PERSON_PRONOUNS.any { lower.contains(it) }) return resolvePersonText(lower, original)
        if (APP_PRONOUNS.any { lower.contains(it) })    return resolveAppText(lower, original)
        if (MEDIA_PRONOUNS.any { lower.contains(it) })  return resolveMediaText(lower, original)

        return TextResult.NoContext(original)
    }

    // ── Person ─────────────────────────────────────────────────────────────
    private fun resolvePersonText(lower: String, original: String): TextResult {
        val ctx = ConversationContextManager.lastPerson()
            ?: return TextResult.Prompt("I no longer know who you're referring to. Who would you like to contact?")

        if (ctx.confidence == ContextConfidence.LOW) return TextResult.NoContext(original)

        // Replace pronoun with real name in the original text
        val resolved = replacePronoun(original, PERSON_PRONOUNS, ctx.contactName)

        return if (ctx.confidence == ContextConfidence.HIGH) {
            TextResult.ResolvedText(resolved)
        } else {
            // MEDIUM: FIX 2 - store pending and return confirmation prompt
            pendingContextText = resolved
            TextResult.Prompt("Did you mean ${ctx.contactName}? Say yes or no.")
        }
    }

    // ── App ──────────────────────────────────────────────────────────────
    private fun resolveAppText(lower: String, original: String): TextResult {
        val ctx = ConversationContextManager.lastApp()
            ?: return TextResult.Prompt("I no longer know which app you mean. Which app?")

        if (ctx.confidence == ContextConfidence.LOW) return TextResult.NoContext(original)

        val resolved = replacePronoun(original, APP_PRONOUNS, ctx.appName)

        return if (ctx.confidence == ContextConfidence.HIGH) {
            TextResult.ResolvedText(resolved)
        } else {
            pendingContextText = resolved
            TextResult.Prompt("Did you mean ${ctx.appName}? Say yes or no.")
        }
    }

    // ── Media ─────────────────────────────────────────────────────────────
    private fun resolveMediaText(lower: String, original: String): TextResult {
        val ctx = ConversationContextManager.lastMedia()
            ?: return TextResult.Prompt("I no longer know which media you mean. What would you like to play?")

        if (ctx.confidence == ContextConfidence.LOW) return TextResult.NoContext(original)

        val name = ctx.song ?: ctx.artist ?: ctx.video ?: ctx.playlist ?: return TextResult.NoContext(original)
        val resolved = replacePronoun(original, MEDIA_PRONOUNS, name)

        return if (ctx.confidence == ContextConfidence.HIGH) {
            TextResult.ResolvedText(resolved)
        } else {
            pendingContextText = resolved
            TextResult.Prompt("Did you mean $name? Say yes or no.")
        }
    }

    // ── Pronoun replacement ────────────────────────────────────────────────
    private fun replacePronoun(original: String, pronouns: Set<String>, replacement: String): String {
        var result = original
        // Replace longest match first to avoid partial replacements
        for (pronoun in pronouns.sortedByDescending { it.length }) {
            val idx = result.lowercase().indexOf(pronoun)
            if (idx >= 0) {
                result = result.substring(0, idx) + replacement + result.substring(idx + pronoun.length)
                break
            }
        }
        return result
    }

    /** Clear any pending medium context (e.g. on session end). */
    fun clearPending() { pendingContextText = null }
}
