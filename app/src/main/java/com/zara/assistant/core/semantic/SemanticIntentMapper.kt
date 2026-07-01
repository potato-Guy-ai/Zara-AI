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
                // No REMINDER action exists yet in IntentAction — map to SET_TIMER,
                // which is the closest existing action. Duration entity
                // ("2 hours") maps to IntentExtra.DURATION for SlotExtractor.
                val duration = result.entities["duration"]
                ZaraIntent(
                    type    = IntentType.ACTION,
                    action  = IntentAction.SET_TIMER,
                    extra   = buildMap {
                        if (!duration.isNullOrBlank()) put(IntentExtra.DURATION, duration)
                    },
                    rawText = originalText
                )
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
                // EntityExtractor does not yet extract a destination for NAVIGATION;
                // pass the raw text as target so ActionExecutor can handle it.
                ZaraIntent(
                    type    = IntentType.ACTION,
                    action  = IntentAction.NAVIGATE_TO,
                    target  = originalText,
                    rawText = originalText
                )
            }

            SemanticIntent.APP_CONTROL -> {
                // EntityExtractor does not yet extract an app name for APP_CONTROL;
                // pass the raw text as target for AppActionPlanner to resolve.
                ZaraIntent(
                    type    = IntentType.ACTION,
                    action  = IntentAction.OPEN_APP,
                    target  = originalText,
                    rawText = originalText
                )
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
