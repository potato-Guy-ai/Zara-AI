package com.zara.assistant.execution

import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.5A — Confirmation Manager.
 * Generic confirmation engine reusable for messages, payments, deletions.
 * Session-only. No persistence.
 */
object ConfirmationManager {

    private var pending: ConfirmationRequest? = null

    private val YES_WORDS    = setOf("yes", "yeah", "yep", "sure", "ok", "okay", "send", "confirm", "do it")
    private val NO_WORDS     = setOf("no", "nope", "cancel", "stop", "never mind", "nevermind", "don't")

    // Actions that require confirmation before execution
    private val CONFIRMATION_ACTIONS = setOf(
        "SEND_WHATSAPP", "SEND_SMS", "CALL"
    )

    fun requiresConfirmation(action: String): Boolean = CONFIRMATION_ACTIONS.contains(action)

    fun hasPending(): Boolean = pending != null

    fun store(request: ConfirmationRequest) {
        pending = request
        ZaraLogger.d("[Confirmation] pending: ${request.prompt}")
    }

    fun clear() { pending = null }

    /**
     * Try to resolve user input against pending confirmation.
     * Returns:
     *   true  → confirmed, execute
     *   false → cancelled
     *   null  → not a yes/no, prompt again
     */
    fun resolve(userText: String): Boolean? {
        val p = pending ?: return null
        val lower = userText.trim().lowercase()
        return when {
            YES_WORDS.any { lower == it || lower.startsWith("$it ") } -> {
                pending = null; true
            }
            NO_WORDS.any { lower == it || lower.startsWith("$it ") } -> {
                pending = null; false
            }
            else -> null
        }
    }

    fun getPending(): ConfirmationRequest? = pending
}
