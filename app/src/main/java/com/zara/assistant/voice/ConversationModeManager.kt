package com.zara.assistant.voice

import com.zara.assistant.core.IntentType
import com.zara.assistant.core.ZaraIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Layer 6.6 — Voice Conversation Loop.
 *
 * Tracks the live multi-turn conversation window opened by a wakeword
 * session. While ACTIVE, conversational follow-ups reopen the mic without
 * requiring the wakeword again. Action commands, stop phrases, or a 15s
 * silence timeout end the window.
 *
 * Lightweight by design: a single cancellable coroutine `Job` provides the
 * timeout. No polling, no Handler/Timer, no services, no extra threads —
 * it rides on the caller's existing CoroutineScope (VoiceSessionManager's).
 */
object ConversationModeManager {

    private const val TIMEOUT_MS = 15_000L

    var state: ConversationModeState = ConversationModeState.INACTIVE
        private set

    val isActive: Boolean get() = state == ConversationModeState.ACTIVE

    private var timeoutJob: Job? = null

    /** Enter conversation mode (called when a wakeword session begins) and arm the timeout. */
    fun activate(scope: CoroutineScope, onTimeout: () -> Unit) {
        state = ConversationModeState.ACTIVE
        rearmTimeout(scope, onTimeout)
    }

    /** Reset the 15s silence window — call each time the mic reopens for a follow-up turn. */
    fun rearmTimeout(scope: CoroutineScope, onTimeout: () -> Unit) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(TIMEOUT_MS)
            if (isActive) {
                deactivate()
                onTimeout()
            }
        }
    }

    /** Stop input was received in time — cancel the pending timeout without ending the mode. */
    fun cancelTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    /** End conversation mode: action command, stop phrase, timeout, or explicit stop. */
    fun deactivate() {
        state = ConversationModeState.INACTIVE
        cancelTimeout()
    }
}

/**
 * Simple intent classifier for the conversation loop.
 * Action intents (OPEN_APP, CALL, NAVIGATE_TO, etc.) end the loop.
 * Conversational/chat/cloud-query intents keep it alive.
 */
fun isConversational(intent: ZaraIntent?): Boolean {
    if (intent == null) return false
    return intent.type == IntentType.CONVERSATION || intent.type == IntentType.CLOUD
}
