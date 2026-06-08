package com.zara.assistant.execution

import com.zara.assistant.core.IntentAction
import com.zara.assistant.core.IntentExtra
import com.zara.assistant.core.ZaraIntent

/**
 * Layer 6.5A — Execution Planner.
 * Converts a ZaraIntent into an ExecutionPlan with requirements and priority.
 * Wraps execution contracts from Layer 5.7 ExecutionValidator.
 */
object ExecutionPlanner {

    private var idCounter = 0
    private fun nextId() = "plan_${++idCounter}"

    fun plan(intent: ZaraIntent, priority: Priority = Priority.NORMAL): ExecutionPlan {
        val requirements = mutableSetOf<ExecutionRequirement>()

        when (intent.action) {
            IntentAction.CALL, IntentAction.SEND_WHATSAPP, IntentAction.SEND_SMS -> {
                requirements.add(ExecutionRequirement.CONTACT_REQUIRED)
                if (intent.action != IntentAction.CALL) {
                    requirements.add(ExecutionRequirement.CONFIRMATION_REQUIRED)
                }
            }
            IntentAction.OPEN_APP, IntentAction.PLAY_MUSIC, IntentAction.NAVIGATE_TO -> {
                requirements.add(ExecutionRequirement.APP_REQUIRED)
            }
            IntentAction.SET_WIFI, IntentAction.SET_BLUETOOTH -> {
                requirements.add(ExecutionRequirement.INTERNET_REQUIRED)
            }
        }

        return ExecutionPlan(
            id           = nextId(),
            intent       = intent,
            requirements = requirements,
            priority     = priority
        )
    }
}
