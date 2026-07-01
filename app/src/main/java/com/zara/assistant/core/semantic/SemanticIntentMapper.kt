package com.zara.assistant.core.semantic

import com.zara.assistant.core.IntentAction
import com.zara.assistant.core.IntentExtra
import com.zara.assistant.core.IntentType
import com.zara.assistant.core.ZaraIntent

/**
 * Layer 6.6 — SemanticIntentMapper.
 *
 * Converts a [SemanticResult] produced by [SemanticIntentEngine] into a
 * [ZaraIntent] that the existing pipeline (IntentRouter → ActionExecutor)
 * can process without any changes to those components.
 *
 * Returns null when:
 *   - result.fallbackRequired == true  (MiniLM wasn't confident enough)
 *   - result.confidence < THRESHOLD    (low-confidence inference)
 *   - result.intent == UNKNOWN         (no classification was possible)
 *   - no mapping exists for the intent (should not occur; defensive guard)
 *
 * When null is returned, LocalIntentClassifier falls through to the existing
 * cloud fallback, preserving the prior behavior exactly.
 *
 * Entity mapping uses the keys produced by [EntityExtractor] and translates
 * them into the [IntentExtra] keys already consumed by ActionExecutor /
 * SlotExtractor / EntityResolver — no downstream changes required.
 *
 * Fix 1 (layer6-6-minilm-intent patch): NAVIGATION and APP_CONTROL return null.
 *   Entity extraction for destination and app_name is not yet implemented;
 *   passing raw full text as target caused downstream resolver failures.
 *   Both intents now fall through to cloud until entity extraction is ready.
 *
 * Fix 2 (layer6-6-minilm-intent patch): REMINDER maps to SET_TIMER ONLY for
 *   simple timer-style reminders (duration present, no task/contact/body entity).
 *   Reminders with a task, contact, or body entity return null → cloud fallback,
 *   which preserves all slot data and handles them correctly.
 */
object SemanticIntentMapper {

    private const val THRESHOLD = 0.6f

    fun map(result: SemanticResult, originalText: String): ZaraIntent? {
        if (result.fallbackRequired) return null
        if (result.confidence < THRESHOLD) return null
        if (result.intent == SemanticIntent.UNKNOWN) return null

        return when (result.intent) {

            SemanticIntent.MESSAGING -> {
                // Prefer SEND_WHATSAPP as the default for semantically-detected
                // messaging intents (no explicit channel keyword present when
                // MiniLM is the classifier — same default as MessageNLU).
                val contact = result.entities["contact"]
                val body    = result.entities["message"]
                ZaraIntent(
                    type    = IntentType.ACTION,
                    action  = IntentAction.SEND_WHATSAPP,
                    target  = contact,
                    extra   = buildMap {
                        if (!body.isNullOrBlank())    put(IntentExtra.BODY, body)
                        if (!contact.isNullOrBlank()) put(IntentExtra.CONTACT_NAME, contact)
                        put(IntentExtra.CHANNEL, "whatsapp")
                    },
                    rawText = originalText
                )
            }

            SemanticIntent.CALL -> {
                val contact = result.entities["contact"]
                ZaraIntent(
                    type    = IntentType.ACTION,
                    action  = IntentAction.CALL,
                    target  = contact,
                    extra   = buildMap {
                        if (!contact.isNullOrBlank()) put(IntentExtra.CONTACT_NAME, contact)
                    },
                    rawText = originalText
                )
            }

            SemanticIntent.MUSIC -> {
                // song entity from EntityExtractor.extractMusic() → IntentExtra.CONTENT
                // app entity → IntentExtra.APP
                val song = result.entities["song"]
                val app  = result.entities["app"]
                ZaraIntent(
                    type    = IntentType.ACTION,
                    action  = IntentAction.PLAY_MUSIC,
                    target  = song,
                    extra   = buildMap {
                        if (!song.isNullOrBlank()) put(IntentExtra.CONTENT, song)
                        if (!app.isNullOrBlank())  put(IntentExtra.APP, app)
                    },
                    rawText = originalText
                )
            }

            SemanticIntent.REMINDER -> {
                // Fix 2: only map to SET_TIMER for simple "remind me in X" patterns —
                // i.e. duration is present AND no task/contact/body entity is present.
                // Anything with a task, contact, or body needs cloud to handle slots correctly.
                val duration = result.entities["duration"]
                val hasTask  = !result.entities["task"].isNullOrBlank()
                        || !result.entities["contact"].isNullOrBlank()
                        || !result.entities["message"].isNullOrBlank()
                        || !result.entities["body"].isNullOrBlank()

                if (!duration.isNullOrBlank() && !hasTask) {
                    ZaraIntent(
                        type    = IntentType.ACTION,
                        action  = IntentAction.SET_TIMER,
                        extra   = mapOf(IntentExtra.DURATION to duration),
                        rawText = originalText
                    )
                } else {
                    null  // cloud fallback — preserves full intent context
                }
            }

            SemanticIntent.SEARCH -> {
                ZaraIntent(
                    type    = IntentType.ACTION,
                    action  = IntentAction.SEARCH_QUERY,
                    target  = originalText,
                    rawText = originalText
                )
            }

            SemanticIntent.NAVIGATION -> {
                // Fix 1: EntityExtractor does not yet extract a destination.
                // Returning raw full text as target caused downstream resolver failures.
                // Return null until destination entity extraction is implemented.
                null
            }

            SemanticIntent.APP_CONTROL -> {
                // Fix 1: EntityExtractor does not yet extract an app name.
                // Returning raw full text as target caused AppActionPlanner failures.
                // Return null until app_name entity extraction is implemented.
                null
            }

            SemanticIntent.SYSTEM_CONTROL -> {
                // No single SYSTEM_CONTROL action exists; route to cloud for
                // further disambiguation. Returning null here is correct — the
                // caller (LocalIntentClassifier) will fall through to cloudIntent().
                null
            }

            SemanticIntent.KNOWLEDGE_QUERY -> {
                // Knowledge queries belong to cloud, same as the existing
                // reKnowledge branch. Return null; caller routes to cloudIntent().
                null
            }

            SemanticIntent.UNKNOWN -> null  // defensive; already guarded above
        }
    }
}
