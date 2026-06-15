package com.zara.assistant.continuation

import com.zara.assistant.core.ZaraIntent
import com.zara.assistant.execution.ConfirmationManager
import com.zara.assistant.execution.ExecutionQueue
import com.zara.assistant.execution.RecoveryManager
import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.5C — Continuation Resolver.
 *
 * Stateless signal classifier + priority-ordered dispatcher.
 * Called at the top of VoiceSessionManager.processInput() BEFORE the NLP pipeline.
 *
 * Resolution priority:
 *   1. ConfirmationManager (CONFIRM / REJECT)
 *   2. RecoveryManager     (RETRY / RESUME)
 *   3. ExecutionQueue      (CONTINUE / NEXT)
 *   4. ContinuationContext.TASK_REGISTRY (PAUSE / PREVIOUS — architecture only, no execution)
 *   5. If no active system: safety message or null (fall through to NLP)
 *
 * Returns:
 *   non-null String — consume the turn, return this response.
 *   null            — not a continuation signal, or no active continuation; pass to NLP.
 *
 * SAFETY: If user says "yes" and nothing awaits confirmation → explicit message.
 */
object ContinuationResolver {

    // ── Signal maps ──────────────────────────────────────────────────
    private val CONFIRM_WORDS  = setOf("yes", "yeah", "yep", "sure", "ok", "okay",
                                       "send it", "do it", "proceed", "confirm", "send")
    private val REJECT_WORDS   = setOf("no", "nope", "cancel", "stop", "don't",
                                       "leave it", "never mind", "nevermind")
    private val RETRY_WORDS    = setOf("retry", "try again")
    private val RESUME_WORDS   = setOf("resume", "continue", "next", "proceed")
    private val PAUSE_WORDS    = setOf("pause")
    private val PREVIOUS_WORDS = setOf("previous", "go back")

    /** Classify raw text to a ContinuationType. Returns null if not a continuation signal. */
    fun classify(text: String): ContinuationType? {
        val t = text.trim().lowercase()
        return when {
            CONFIRM_WORDS.any  { t == it || t.startsWith("$it ") } -> ContinuationType.CONFIRM
            REJECT_WORDS.any   { t == it || t.startsWith("$it ") } -> ContinuationType.REJECT
            RETRY_WORDS.any    { t == it || t.startsWith("$it ") } -> ContinuationType.RETRY
            RESUME_WORDS.any   { t == it || t.startsWith("$it ") } -> ContinuationType.RESUME
            PAUSE_WORDS.any    { t == it }                         -> ContinuationType.PAUSE
            PREVIOUS_WORDS.any { t == it || t.startsWith("$it ") } -> ContinuationType.PREVIOUS
            else -> null
        }
    }

    /**
     * Main entry point. Returns a response string if this turn is a continuation,
     * or null to fall through to the NLP pipeline.
     *
     * @param text        Raw corrected user input.
     * @param onExecute   Suspend lambda: given a ZaraIntent, executes it and returns response string.
     *                    Provided by VoiceSessionManager to avoid circular dependency.
     */
    suspend fun resolve(
        text: String,
        onExecute: suspend (ZaraIntent) -> String
    ): String? {
        val signal = classify(text) ?: return null

        // ── Priority 1: ConfirmationManager ───────────────────────────────────
        if (ConfirmationManager.hasPending()) {
            return when (signal) {
                ContinuationType.CONFIRM -> {
                    val request = ConfirmationManager.pop()
                    if (request != null) {
                        ExecutionQueue.markWaitingCompleted(request.planId)
                        ContinuationContext.deactivate(ContinuationScope.CONFIRMATION)
                        ZaraLogger.d("[Continuation] CONFIRM → executing ${request.plan.intent.action}")
                        onExecute(request.plan.intent)
                    } else "Nothing is waiting for confirmation."
                }
                ContinuationType.REJECT, ContinuationType.CANCEL -> {
                    val waiting = ExecutionQueue.getWaiting()
                    if (waiting != null) ExecutionQueue.markWaitingCancelled(waiting.plan.id)
                    ConfirmationManager.clear()
                    ContinuationContext.deactivate(ContinuationScope.CONFIRMATION)
                    ZaraLogger.d("[Continuation] REJECT confirmation")
                    "Okay, cancelled."
                }
                else -> "Please say yes or no."
            }
        }

        // ── Priority 2: RecoveryManager ──────────────────────────────────────
        if (RecoveryManager.hasRecoverable()) {
            return when (signal) {
                ContinuationType.RETRY, ContinuationType.RESUME, ContinuationType.CONTINUE -> {
                    val failure = RecoveryManager.popForRetry()
                    if (failure != null) {
                        ContinuationContext.deactivate(ContinuationScope.RECOVERY)
                        ZaraLogger.d("[Continuation] RETRY → ${failure.planId}")
                        onExecute(failure.intent)
                    } else "Nothing to retry."
                }
                ContinuationType.REJECT, ContinuationType.CANCEL -> {
                    RecoveryManager.clear()
                    ContinuationContext.deactivate(ContinuationScope.RECOVERY)
                    "Okay, recovery cancelled."
                }
                else -> null  // not a recovery signal — fall through
            }
        }

        // ── Priority 3: Workflow continuation (ExecutionQueue PENDING items) ──
        if (ContinuationContext.isActive(ContinuationScope.WORKFLOW)) {
            return when (signal) {
                ContinuationType.CONTINUE, ContinuationType.NEXT, ContinuationType.RESUME -> {
                    val next = ExecutionQueue.dequeueNext()
                    if (next != null) {
                        ZaraLogger.d("[Continuation] WORKFLOW NEXT → ${next.plan.id}")
                        onExecute(next.plan.intent)
                    } else {
                        ContinuationContext.deactivate(ContinuationScope.WORKFLOW)
                        "Workflow is complete."
                    }
                }
                ContinuationType.CANCEL, ContinuationType.REJECT -> {
                    ExecutionQueue.cancelAll()
                    ContinuationContext.deactivate(ContinuationScope.WORKFLOW)
                    "Workflow cancelled."
                }
                else -> null
            }
        }

        // ── Priority 4: TaskRegistry (PAUSE/PREVIOUS — architecture only) ──
        if (signal == ContinuationType.PAUSE || signal == ContinuationType.PREVIOUS) {
            // No execution logic yet. Reserved.
            ZaraLogger.d("[Continuation] $signal received — no active task registry handler")
            return null  // fall through to NLP
        }

        // ── Safety: signal matched but no active system ───────────────────────
        return when (signal) {
            ContinuationType.CONFIRM                              -> "Nothing is waiting for confirmation."
            ContinuationType.RETRY, ContinuationType.RESUME      -> "Nothing to retry or resume."
            ContinuationType.CONTINUE, ContinuationType.NEXT     -> null  // treat as normal speech
            ContinuationType.CANCEL, ContinuationType.REJECT     -> null  // handled upstream in VSM
            else                                                  -> null
        }
    }
}
