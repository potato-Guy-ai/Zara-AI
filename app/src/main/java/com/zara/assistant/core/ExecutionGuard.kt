package com.zara.assistant.core

import com.zara.assistant.core.AppActionPlanner
import com.zara.assistant.core.ExecutionContract
import com.zara.assistant.core.ExecutionValidator
import com.zara.assistant.core.IntentExtra
import com.zara.assistant.core.ZaraIntent

/**
 * Layer 5.7 — Execution Guard.
 *
 * Sits between AppActionPlanner and ActionExecutor.
 * Responsibilities:
 *   1. Convert AppActionPlan slots into validated ExecutionContract
 *   2. For compound flows: produce one contract per action, isolate failures
 *   3. Provide fallback contract when validation fails
 *
 * Stores contract back into intent.extra for ActionExecutor consumption.
 */
object ExecutionGuard {

    // Keys written into intent.extra
    const val KEY_CONTRACT_SAFE     = "ec_safe"
    const val KEY_CONTRACT_ACTION   = "ec_action"
    const val KEY_CONTRACT_APP      = "ec_app"
    const val KEY_CONTRACT_TARGET   = "ec_target"
    const val KEY_CONTRACT_QUERY    = "ec_query"
    const val KEY_CONTRACT_FALLBACK = "ec_fallback"
    const val KEY_CONTRACT_READY    = "ec_ready"   // "true" when contract present

    /**
     * Validate planned intent and attach ExecutionContract into extra.
     * If no AppActionPlan present, returns intent unchanged.
     */
    fun guard(intent: ZaraIntent): ZaraIntent {
        val app    = intent.extra[AppActionPlanner.KEY_APP]    ?: return intent
        val action = intent.extra[AppActionPlanner.KEY_ACTION] ?: return intent
        val target = intent.extra[AppActionPlanner.KEY_TARGET]
        val query  = intent.extra[AppActionPlanner.KEY_QUERY]

        val contract = ExecutionValidator.validate(app, action, target, query)

        val newExtra = intent.extra.toMutableMap()
        newExtra[KEY_CONTRACT_READY]  = "true"
        newExtra[KEY_CONTRACT_SAFE]   = contract.safe.toString()
        newExtra[KEY_CONTRACT_ACTION] = contract.action
        newExtra[KEY_CONTRACT_APP]    = contract.app
        if (contract.target   != null) newExtra[KEY_CONTRACT_TARGET]   = contract.target
        if (contract.query    != null) newExtra[KEY_CONTRACT_QUERY]    = contract.query
        if (contract.fallbackAction != null) newExtra[KEY_CONTRACT_FALLBACK] = contract.fallbackAction

        return intent.copy(extra = newExtra)
    }

    /** Read contract back from intent.extra. Returns null if not present. */
    fun readContract(intent: ZaraIntent): ExecutionContract? {
        if (intent.extra[KEY_CONTRACT_READY] != "true") return null
        return ExecutionContract(
            safe           = intent.extra[KEY_CONTRACT_SAFE]   == "true",
            retryable      = false,
            action         = intent.extra[KEY_CONTRACT_ACTION] ?: return null,
            app            = intent.extra[KEY_CONTRACT_APP]    ?: return null,
            target         = intent.extra[KEY_CONTRACT_TARGET],
            query          = intent.extra[KEY_CONTRACT_QUERY],
            fallbackAction = intent.extra[KEY_CONTRACT_FALLBACK]
        )
    }
}
