package com.zara.assistant.cloud

import android.content.Context

/**
 * Cloud reasoning abstraction.
 * Only called when IntentRouter decides CLOUD is needed.
 * Never sends: contacts, memory, raw commands.
 */
interface AiProvider {
    suspend fun query(prompt: String): String
}

/**
 * Singleton client. Returns null if no provider configured.
 */
class CloudReasoningClient private constructor(
    private val provider: AiProvider
) {
    suspend fun query(prompt: String): String = provider.query(prompt)

    companion object {
        @Volatile private var instance: CloudReasoningClient? = null

        fun getInstance(context: Context): CloudReasoningClient? = instance

        fun configure(provider: AiProvider) {
            instance = CloudReasoningClient(provider)
        }

        fun clear() { instance = null }
    }
}
