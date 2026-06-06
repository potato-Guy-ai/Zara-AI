package com.zara.assistant.core

import android.content.Context
import com.zara.assistant.actions.ActionExecutor
import com.zara.assistant.core.IntentType.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Central orchestrator — routes ZaraIntent to the correct handler.
 * Layer 5.3: Clarification check inserted before normal routing.
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

    /**
     * Layer 5.3: Try clarification resolution before normal classification.
     * Called by VoiceSessionManager with raw corrected text.
     * Returns resolved response string if clarification handled, else null.
     */
    suspend fun tryResolveClarification(userText: String): String? = withContext(Dispatchers.Default) {
        if (!ClarificationManager.hasPending()) return@withContext null

        val resolved = ClarificationManager.resolve(userText)
        return@withContext when {
            resolved != null -> {
                // Clarification succeeded — execute the rebuilt intent
                actionExecutor.execute(resolved)
            }
            userText.trim().lowercase().let { t ->
                t == "cancel" || t == "stop" || t == "never mind" || t == "nevermind"
            } -> {
                ClarificationManager.clear()
                "Okay, cancelled."
            }
            else -> {
                // Pending but unresolved — re-prompt
                "I didn't catch that. Please say the name or number of your choice."
            }
        }
    }

    private suspend fun routeToCloud(intent: ZaraIntent): String {
        if (!privacyFilter.isSafeForCloud(intent)) {
            return "I can't send that to the cloud."
        }
        val sanitized = privacyFilter.sanitizeIntent(intent)
        return try {
            com.zara.assistant.cloud.CloudReasoningClient.getInstance()
                ?.query(sanitized) ?: "Cloud AI not configured."
        } catch (e: Exception) {
            "Couldn't reach cloud reasoning right now."
        }
    }
}
