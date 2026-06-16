package com.zara.assistant.continuation

import com.zara.assistant.core.ZaraIntent
import com.zara.assistant.execution.ConfirmationManager
import com.zara.assistant.execution.ExecutionQueue
import com.zara.assistant.execution.RecoveryManager
import com.zara.assistant.media.MediaControlAction
import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.5C + 6.5F Priority Fix
 *
 * Priority fix: before Recovery handling, check if the text is a media-control
 * phrase (via MediaControlAction.fromText). If it is, return null immediately
 * so the NLP pipeline routes it to MEDIA_CONTROL.
 *
 * This preserves:
 *   "resume"       → Recovery (bare, no qualifier)
 *   "resume music" → MEDIA_CONTROL (has media qualifier)
 *   "next song"    → MEDIA_CONTROL
 *   "pause music"  → MEDIA_CONTROL
 *   "stop music"   → MEDIA_CONTROL
 *   "stop"         → cancellation block (unchanged)
 */
object ContinuationResolver {

    private val CONFIRM_WORDS  = setOf("yes", "yeah", "yep", "sure", "ok", "okay",
                                       "send it", "do it", "proceed", "confirm", "send")
    private val REJECT_WORDS   = setOf("no", "nope", "cancel", "stop", "don't",
                                       "leave it", "never mind", "nevermind")
    private val RETRY_WORDS    = setOf("retry", "try again")
    private val RESUME_WORDS   = setOf("resume", "continue", "next", "proceed")
    private val PAUSE_WORDS    = setOf("pause")
    private val PREVIOUS_WORDS = setOf("previous", "go back")

    // Signals that Recovery would consume and that may conflict with media commands
    private val RECOVERY_SIGNALS = setOf(
        ContinuationType.RETRY,
        ContinuationType.RESUME,
        ContinuationType.CONTINUE
    )

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
                        ZaraLogger.d("[Continuation] CONFIRM → ${request.plan.intent.action}")
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
            // 6.5F Priority Fix: if text is a media-control phrase, let NLP handle it.
            // "resume music", "pause music", "next song" etc. must not be eaten by Recovery.
            // Bare "resume", "retry", "continue" still go to Recovery.
            if (signal in RECOVERY_SIGNALS && MediaControlAction.fromText(text) != null) {
                ZaraLogger.d("[Continuation] media phrase detected during recovery, passing to NLP: $text")
                return null
            }

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
                else -> null
            }
        }

        // ── Priority 3: Workflow continuation ─────────────────────────────────
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
            ZaraLogger.d("[Continuation] $signal — no active task registry handler")
            return null
        }

        // ── Safety: signal matched but no active system ───────────────────────
        return when (signal) {
            ContinuationType.CONFIRM                              -> "Nothing is waiting for confirmation."
            ContinuationType.RETRY, ContinuationType.RESUME      -> "Nothing to retry or resume."
            ContinuationType.CONTINUE, ContinuationType.NEXT     -> null
            ContinuationType.CANCEL, ContinuationType.REJECT     -> null
            else                                                  -> null
        }
    }
}
