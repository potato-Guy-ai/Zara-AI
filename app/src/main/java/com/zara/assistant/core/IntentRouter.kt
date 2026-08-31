package com.zara.assistant.core

import android.content.Context
import com.zara.assistant.actions.ActionExecutor
import com.zara.assistant.cloud.CloudReasoningClient
import com.zara.assistant.core.IntentType.*
import com.zara.assistant.knowledge.KnowledgeBase
import com.zara.assistant.memory.MemoryManager
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
        val question = privacyFilter.sanitizeIntent(intent)
        return try {
            val client = CloudReasoningClient.getInstance()
                ?: return "Cloud AI not configured."
            routeWithKnowledge(client, question)
        } catch (e: Exception) {
            "Couldn't reach cloud reasoning right now."
        }
    }

    /**
     * Phase F — Strict Q&A knowledge injection boundary.
     *
     * Knowledge is ONLY available inside an explicitly eligible informational
     * question. It is never injected because a query is ambiguous, long,
     * cloud-routed, or locally unclassified — such inputs get the plain cloud
     * prompt. This path is only reachable from CLOUD intents, so device action
     * intents (CALL, SEND_SMS, OPEN_APP, SET_ALARM/TIMER/REMINDER/WIFI,
     * PLAY_MUSIC, MEDIA_CONTROL, ...) can never receive Obsidian content.
     */
    private suspend fun routeWithKnowledge(client: CloudReasoningClient, question: String): String {
        val memory = MemoryManager(context)
        if (KnowledgeBase.isEligibleQuestion(question) && KnowledgeBase.isEnabled(memory)) {
            val knowledge = KnowledgeBase.buildContext(memory, question)
            if (knowledge.isNotBlank()) {
                return client.query(buildKnowledgePrompt(question, knowledge))
            }
        }
        return client.query(question)
    }

    /** Wraps the user question with bounded reference material + security boundary. */
    private fun buildKnowledgePrompt(question: String, knowledge: String): String = buildString {
        append("You are Zara, a helpful assistant. Answer the user's question using the ")
        append("reference material below when relevant. If it does not contain the answer, say so. ")
        appendLine()
        appendLine()
        appendLine(KnowledgeBase.KNOWLEDGE_BOUNDARY_PROMPT)
        appendLine()
        appendLine("--- Reference material ---")
        appendLine(knowledge)
        appendLine()
        appendLine("--- User question ---")
        append(question)
    }
}
