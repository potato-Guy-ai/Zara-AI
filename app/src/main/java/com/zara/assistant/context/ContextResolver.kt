package com.zara.assistant.context

import com.zara.assistant.core.ClarificationManager
import com.zara.assistant.core.IntentExtra
import com.zara.assistant.core.ZaraIntent
import com.zara.assistant.models.ClarificationCandidate
import com.zara.assistant.models.ClarificationEntityType
import com.zara.assistant.models.PendingClarification
import java.util.UUID

/**
 * Batch A1 — Fix 5 + Fix 6
 *
 * Uses currentConfidence (time-decayed) instead of stored confidence.
 * Expiry is now 45s (person/media/query) / 60s (app/action).
 * After expiry, pronoun references return a prompt asking who the user means.
 *
 * Architecture unchanged: ClarificationManager is sole clarification authority.
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
        data class ResolvedText(val text: String) : TextResult()
        data class Prompt(val message: String) : TextResult()
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

        // FIX 6: use currentConfidence (time-decayed), not stored confidence
        return when (ctx.currentConfidence) {
            ContextConfidence.HIGH -> {
                val resolved = replacePronoun(original, PERSON_PRONOUNS, ctx.contactName)
                TextResult.ResolvedText(resolved)
            }
            ContextConfidence.MEDIUM -> {
                val resolved = replacePronoun(original, PERSON_PRONOUNS, ctx.contactName)
                storeMediumClarification(original, resolved, ctx.contactName)
                TextResult.Prompt("Did you mean ${ctx.contactName}? Say yes or no.")
            }
            ContextConfidence.LOW -> {
                // Treat LOW same as expired
                TextResult.Prompt("I no longer know who you're referring to. Who would you like to contact?")
            }
        }
    }

    private fun resolveAppText(lower: String, original: String): TextResult {
        val ctx = ConversationContextManager.lastApp()
            ?: return TextResult.Prompt("I no longer know which app you mean. Which app?")
        return when (ctx.currentConfidence) {
            ContextConfidence.HIGH -> {
                val resolved = replacePronoun(original, APP_PRONOUNS, ctx.appName)
                TextResult.ResolvedText(resolved)
            }
            ContextConfidence.MEDIUM -> {
                val resolved = replacePronoun(original, APP_PRONOUNS, ctx.appName)
                storeMediumClarification(original, resolved, ctx.appName)
                TextResult.Prompt("Did you mean ${ctx.appName}? Say yes or no.")
            }
            ContextConfidence.LOW -> {
                TextResult.Prompt("I no longer know which app you mean. Which app?")
            }
        }
    }

    private fun resolveMediaText(lower: String, original: String): TextResult {
        val ctx = ConversationContextManager.lastMedia()
            ?: return TextResult.Prompt("I no longer know which media you mean. What would you like to play?")
        val name = ctx.song ?: ctx.artist ?: ctx.video ?: ctx.playlist
            ?: return TextResult.NoContext(original)
        return when (ctx.currentConfidence) {
            ContextConfidence.HIGH -> {
                val resolved = replacePronoun(original, MEDIA_PRONOUNS, name)
                TextResult.ResolvedText(resolved)
            }
            ContextConfidence.MEDIUM -> {
                val resolved = replacePronoun(original, MEDIA_PRONOUNS, name)
                storeMediumClarification(original, resolved, name)
                TextResult.Prompt("Did you mean $name? Say yes or no.")
            }
            ContextConfidence.LOW -> {
                TextResult.Prompt("I no longer know which media you mean. What would you like to play?")
            }
        }
    }

    private fun storeMediumClarification(originalText: String, resolvedText: String, entityName: String) {
        val placeholder = ZaraIntent(
            type    = com.zara.assistant.core.IntentType.UNKNOWN,
            action  = com.zara.assistant.core.IntentAction.UNKNOWN,
            rawText = originalText
        )
        ClarificationManager.store(
            PendingClarification(
                clarificationId = UUID.randomUUID().toString(),
                originalIntent  = placeholder,
                entityType      = ClarificationEntityType.CONTEXT,
                candidates      = listOf(
                    ClarificationCandidate(
                        displayName   = entityName,
                        resolvedValue = resolvedText,
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
