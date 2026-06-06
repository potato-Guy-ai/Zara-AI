package com.zara.assistant.core

import com.zara.assistant.core.AppActionPlanner

/**
 * Layer 5.7 — Validates a planned intent and produces an ExecutionContract.
 * Pure, stateless, no Android calls.
 */
object ExecutionValidator {

    fun validate(app: String, action: String, target: String?, query: String?): ExecutionContract {
        val (safe, fallback) = when (action) {
            AppActionPlanner.ACTION_CALL,
            AppActionPlanner.ACTION_AUDIO_CALL,
            AppActionPlanner.ACTION_VIDEO_CALL,
            AppActionPlanner.ACTION_VOICE_MESSAGE -> {
                val ok = !target.isNullOrBlank()
                ok to if (!ok) "open_app" else null
            }
            AppActionPlanner.ACTION_MESSAGE       -> {
                val ok = !target.isNullOrBlank()
                ok to if (!ok) "open_app" else null
            }
            AppActionPlanner.ACTION_SEARCH        -> {
                val ok = !query.isNullOrBlank()
                ok to if (!ok) "open_app" else null
            }
            AppActionPlanner.ACTION_PLAY,
            AppActionPlanner.ACTION_PLAY_SONG,
            AppActionPlanner.ACTION_PLAY_ARTIST,
            AppActionPlanner.ACTION_PLAY_PLAYLIST -> true to null
            AppActionPlanner.ACTION_OPEN_CHAT,
            AppActionPlanner.ACTION_OPEN_VIDEO    -> true to null
            "open_app"                            -> {
                val ok = !app.isBlank()
                ok to null
            }
            else -> true to null   // unknown — allow through, ActionExecutor handles
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
