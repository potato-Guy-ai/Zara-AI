package com.zara.assistant.core

import android.content.Context
import com.zara.assistant.actions.ActionExecutor
import com.zara.assistant.core.IntentType.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Central orchestrator — routes ZaraIntent to the correct handler.
 * Does NOT contain conversation logic (delegated to ConversationEngine).
 */
class IntentRouter(private val context: Context) {

    private val actionExecutor     = ActionExecutor(context)
    private val privacyFilter      = PrivacyFilter()
    private val conversationEngine = ConversationEngine()

    suspend fun route(intent: ZaraIntent): String = withContext(Dispatchers.Default) {
        when (intent.type) {
            ACTION       -> actionExecutor.execute(intent)
            CONVERSATION -> conversationEngine.handle(intent)
            CLOUD        -> routeToCloud(intent)
            UNKNOWN      -> "I didn't understand that. Could you rephrase?"
        }
    }

    private suspend fun routeToCloud(intent: ZaraIntent): String {
        // Gate: only CLOUD intents reach here; sanitize all fields before dispatch
        if (!privacyFilter.isSafeForCloud(intent)) {
            return "I can't send that to the cloud."
        }
        val sanitized = privacyFilter.sanitizeIntent(intent)
        return try {
            com.zara.assistant.cloud.CloudReasoningClient.getInstance(context)
                ?.query(sanitized) ?: "Cloud AI not configured."
        } catch (e: Exception) {
            "Couldn't reach cloud reasoning right now."
        }
    }
}
