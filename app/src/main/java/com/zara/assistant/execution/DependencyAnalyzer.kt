package com.zara.assistant.execution

import com.zara.assistant.core.IntentAction
import com.zara.assistant.core.IntentExtra
import com.zara.assistant.core.ZaraIntent

/**
 * Layer 6.5A — Dependency Analyzer.
 * Determines execution order for compound intents using pure rules.
 * APP_OPEN before SEARCH/PLAY when same app is targeted.
 */
object DependencyAnalyzer {

    // Actions that require an app to be open first
    private val APP_DEPENDENT = setOf(
        IntentAction.QUERY, IntentAction.PLAY_MUSIC
    )
    // Mapping: if the intent targets these apps, it depends on opening that app
    private val APP_OPENERS = setOf(IntentAction.OPEN_APP)

    /**
     * Given a list of ExecutionPlans (one per compound segment),
     * set dependsOnId for plans that depend on a preceding app-open.
     */
    fun analyze(plans: List<ExecutionPlan>): List<ExecutionPlan> {
        if (plans.size <= 1) return plans
        val result = plans.toMutableList()
        var lastAppOpenId: String? = null
        var lastAppName: String? = null

        for (i in result.indices) {
            val plan = result[i]
            val action  = plan.intent.action
            val appSlot = plan.intent.extra[IntentExtra.APP]
                ?: plan.intent.extra[IntentExtra.APP_NAME]

            if (APP_OPENERS.contains(action)) {
                lastAppOpenId = plan.id
                lastAppName   = appSlot
            } else if (APP_DEPENDENT.contains(action) && lastAppOpenId != null) {
                // If this action targets same app (or no app specified), declare dependency
                val sameApp = appSlot == null || appSlot == lastAppName
                if (sameApp) {
                    result[i] = plan.copy(dependsOnId = lastAppOpenId)
                }
            }
        }
        return result
    }
}
