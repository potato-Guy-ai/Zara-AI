package com.zara.assistant.execution

import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.5A + Final Hardening
 *
 * FIX 1: resolve() no longer clears pending on yes.
 *   pop() returns-and-clears; called by VoiceSessionManager after confirmed=true.
 *   Guarantees plan is never null when retrieved after confirmation.
 */
object ConfirmationManager {

    private var pending: ConfirmationRequest? = null

    private val YES_WORDS = setOf("yes", "yeah", "yep", "sure", "ok", "okay", "send", "confirm", "do it")
    private val NO_WORDS  = setOf("no", "nope", "cancel", "stop", "never mind", "nevermind", "don't")

    private val CONFIRMATION_ACTIONS = setOf("SEND_WHATSAPP", "SEND_SMS", "CALL")

    fun requiresConfirmation(action: String): Boolean = CONFIRMATION_ACTIONS.contains(action)

    fun hasPending(): Boolean = pending != null

    fun store(request: ConfirmationRequest) {
        pending = request
        ZaraLogger.d("[Confirmation] pending: ${request.prompt}")
    }

    fun clear() { pending = null }

    /** Read-only peek; does NOT clear. */
    fun getPending(): ConfirmationRequest? = pending

    /**
     * FIX 1: Returns-and-clears the pending request (read-once).
     * Call ONLY after resolve() returns true.
     */
    fun pop(): ConfirmationRequest? {
        val p = pending
        pending = null
        return p
    }

    /**
     * Resolve user input.
     * Returns:
     *   true  → yes (pending NOT cleared here — caller must call pop())
     *   false → no  (pending cleared here)
     *   null  → not a yes/no
     */
    fun resolve(userText: String): Boolean? {
        if (pending == null) return null
        val lower = userText.trim().lowercase()
        return when {
            YES_WORDS.any { lower == it || lower.startsWith("$it ") } -> true  // do NOT clear
            NO_WORDS.any  { lower == it || lower.startsWith("$it ") } -> { pending = null; false }
            else -> null
        }
    }
}
