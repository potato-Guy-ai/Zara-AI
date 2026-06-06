package com.zara.assistant.core

/**
 * Layer 5.7 — Validates planned action and produces ExecutionContract.
 * Pure, stateless, no Android calls.
 */
object ExecutionValidator {

    fun validate(app: String, action: String, target: String?, query: String?): ExecutionContract {
        val (safe, fallback) = when (action) {
            AppActionPlanner.ACTION_CALL,
            AppActionPlanner.ACTION_AUDIO_CALL,
            AppActionPlanner.ACTION_VIDEO_CALL,
            AppActionPlanner.ACTION_VOICE_MESSAGE,
            AppActionPlanner.ACTION_MESSAGE,
            AppActionPlanner.ACTION_OPEN_CHAT -> {
                val ok = !target.isNullOrBlank()
                Pair(ok, if (!ok) "open_app" else null)
            }
            AppActionPlanner.ACTION_SEARCH -> {
                val ok = !query.isNullOrBlank()
                Pair(ok, if (!ok) "open_app" else null)
            }
            else -> Pair(true, null)
        }
        return ExecutionContract(
            safe           = safe,
            retryable      = false,
            action         = action,
            app            = app,
            target         = target,
            query          = query,
            fallbackAction = fallback
        )
    }
}
