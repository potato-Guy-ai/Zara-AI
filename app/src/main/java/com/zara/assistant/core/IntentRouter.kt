package com.zara.assistant.core

import android.content.Context
import com.zara.assistant.actions.ActionExecutor
import com.zara.assistant.core.IntentType.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Central orchestrator.
 * Decides: local action | local conversation | cloud reasoning
 */
class IntentRouter(private val context: Context) {

    private val actionExecutor = ActionExecutor(context)
    private val privacyFilter = PrivacyFilter()

    suspend fun route(intent: ZaraIntent): String = withContext(Dispatchers.Default) {
        return@withContext when (intent.type) {
            ACTION -> actionExecutor.execute(intent)
            CONVERSATION -> handleConversation(intent)
            CLOUD -> routeToCloud(intent)
            UNKNOWN -> "I didn't understand that. Could you rephrase?"
        }
    }

    private fun handleConversation(intent: ZaraIntent): String {
        // Local conversational responses
        return when (intent.action) {
            "TIME" -> {
                val t = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                "It's $t"
            }
            "DATE" -> {
                val d = java.text.SimpleDateFormat("EEEE, MMMM d", java.util.Locale.getDefault()).format(java.util.Date())
                "Today is $d"
            }
            "GREETING" -> "I'm here. What do you need?"
            "STOP" -> "Going quiet. Say Hey Zara to wake me."
            else -> "I'm still learning that one."
        }
    }

    private suspend fun routeToCloud(intent: ZaraIntent): String {
        val filtered = privacyFilter.sanitize(intent.rawText)
        return try {
            com.zara.assistant.cloud.CloudReasoningClient.getInstance(context)
                ?.query(filtered) ?: "Cloud AI not configured."
        } catch (e: Exception) {
            "Couldn't reach cloud reasoning right now."
        }
    }
}
