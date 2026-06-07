package com.zara.assistant.context

import com.zara.assistant.core.IntentAction
import com.zara.assistant.core.IntentExtra
import com.zara.assistant.core.ZaraIntent

/**
 * Layer 6.0 — Context Updater.
 * Updates ConversationContextManager ONLY after successful execution.
 * Must be called by IntentRouter/ActionExecutor post-execution.
 */
object ContextUpdater {

    fun update(intent: ZaraIntent, executionResult: String) {
        // Only update on clearly successful results
        if (executionResult.startsWith("Something went wrong") ||
            executionResult.startsWith("Couldn't") ||
            executionResult.startsWith("I couldn't") ||
            executionResult.startsWith("I need more") ||
            executionResult.startsWith("I found multiple") ||
            executionResult.startsWith("I no longer")) return

        // Person context
        val contactName = intent.extra[IntentExtra.CONTACT_NAME]
        if (!contactName.isNullOrBlank()) {
            ConversationContextManager.updatePerson(
                name       = contactName,
                phone      = intent.extra[IntentExtra.PHONE_NUMBER],
                confidence = ContextConfidence.HIGH
            )
        }

        // App context
        val appName = intent.extra[IntentExtra.APP_NAME] ?: intent.extra[IntentExtra.APP]
        if (!appName.isNullOrBlank()) {
            ConversationContextManager.updateApp(
                appName     = appName,
                packageName = intent.extra[IntentExtra.APP_PACKAGE],
                confidence  = ContextConfidence.HIGH
            )
        }

        // Media context
        val content = intent.extra[IntentExtra.CONTENT]
        val artist  = intent.extra[IntentExtra.ARTIST]
        val song    = intent.extra[IntentExtra.SONG]
        if (content != null || artist != null || song != null) {
            ConversationContextManager.updateMedia(
                song       = song ?: content,
                artist     = artist,
                playlist   = null,
                video      = if (intent.extra[IntentExtra.APP_NAME]?.contains("youtube", true) == true) content else null,
                confidence = ContextConfidence.HIGH
            )
        }

        // Query context
        val query = intent.extra[IntentExtra.QUERY]
        if (!query.isNullOrBlank()) {
            ConversationContextManager.updateQuery(
                query     = query,
                queryType = if (intent.action == IntentAction.NAVIGATE_TO) "navigation" else "search",
                confidence = ContextConfidence.HIGH
            )
        }

        // Action context
        ConversationContextManager.updateAction(
            action     = intent.action,
            target     = intent.target,
            confidence = ContextConfidence.HIGH
        )
    }
}
