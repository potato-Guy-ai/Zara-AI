package com.zara.assistant.core

/**
 * Layer 5.7 — Execution Guard.
 * Converts AppActionPlan slots into validated ExecutionContract stored in intent.extra.
 */
object ExecutionGuard {

    const val KEY_CONTRACT_SAFE     = "ec_safe"
    const val KEY_CONTRACT_ACTION   = "ec_action"
    const val KEY_CONTRACT_APP      = "ec_app"
    const val KEY_CONTRACT_TARGET   = "ec_target"
    const val KEY_CONTRACT_QUERY    = "ec_query"
    const val KEY_CONTRACT_FALLBACK = "ec_fallback"
    const val KEY_CONTRACT_READY    = "ec_ready"

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
        if (contract.target        != null) newExtra[KEY_CONTRACT_TARGET]   = contract.target
        if (contract.query         != null) newExtra[KEY_CONTRACT_QUERY]    = contract.query
        if (contract.fallbackAction != null) newExtra[KEY_CONTRACT_FALLBACK] = contract.fallbackAction

        return intent.copy(extra = newExtra)
    }

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
