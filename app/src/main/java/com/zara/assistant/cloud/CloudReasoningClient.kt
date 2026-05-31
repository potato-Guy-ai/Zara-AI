package com.zara.assistant.cloud

/**
 * Cloud reasoning abstraction.
 * Only called when IntentRouter decides CLOUD is needed.
 * Never receives: contact names, SMS bodies, memory, raw device commands.
 *
 * C7 fix: getInstance() no longer accepts a Context parameter it never used.
 * All call sites updated accordingly.
 */
interface AiProvider {
    suspend fun query(prompt: String): String
}

class CloudReasoningClient private constructor(
    private val provider: AiProvider
) {
    suspend fun query(prompt: String): String = provider.query(prompt)

    companion object {
        @Volatile private var instance: CloudReasoningClient? = null

        /** Returns the configured provider, or null if cloud has not been set up. */
        fun getInstance(): CloudReasoningClient? = instance

        /** Call once from ZaraApplication when the user opts into cloud reasoning. */
        fun configure(provider: AiProvider) {
            instance = CloudReasoningClient(provider)
        }

        fun clear() { instance = null }
    }
}
