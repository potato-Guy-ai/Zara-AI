package com.zara.assistant.execution

import com.zara.assistant.core.IntentExtra
import com.zara.assistant.core.ZaraIntent
import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.5A — Conflict Resolver.
 * Detects contradictory commands. Latest command wins.
 * Pure rules. No AI.
 */
object ConflictResolver {

    // Sets of mutually exclusive extra keys / actions
    private val VOLUME_CONFLICTS = setOf("SET_VOLUME", "SET_SILENT")
    private val WIFI_CONFLICTS   = setOf("wifi_on", "wifi_off")
    private val BT_CONFLICTS     = setOf("bluetooth_on", "bluetooth_off")

    /**
     * Given a list of plans, remove plans superseded by a later contradicting plan.
     * Latest command wins (keep the last of any conflicting pair).
     */
    fun resolve(plans: List<ExecutionPlan>): List<ExecutionPlan> {
        if (plans.size <= 1) return plans
        val result = plans.toMutableList()
        for (i in result.indices) {
            for (j in i + 1 until result.size) {
                if (conflicts(result[i].intent, result[j].intent)) {
                    // Later (j) wins; mark earlier (i) as superseded via CANCELLED requirement
                    // We simply remove i from the output
                    ZaraLogger.d("[ConflictResolver] ${result[i].intent.action} superseded by ${result[j].intent.action}")
                    result[i] = result[i].copy(requirements = result[i].requirements + ExecutionRequirement.CONFIRMATION_REQUIRED)
                }
            }
        }
        // Remove plans flagged as superseded (marked by injecting CONFIRMATION_REQUIRED as a sentinel — remove them)
        // Actually: cleanly filter by tracking which indices were superseded
        val supersededIndices = mutableSetOf<Int>()
        for (i in result.indices) {
            for (j in i + 1 until result.size) {
                if (conflicts(plans[i].intent, plans[j].intent)) supersededIndices.add(i)
            }
        }
        return plans.filterIndexed { idx, _ -> !supersededIndices.contains(idx) }
    }

    private fun conflicts(a: ZaraIntent, b: ZaraIntent): Boolean {
        if (VOLUME_CONFLICTS.contains(a.action) && VOLUME_CONFLICTS.contains(b.action) && a.action != b.action) return true
        val aOn = a.extra[IntentExtra.ON]; val bOn = b.extra[IntentExtra.ON]
        if (a.action == b.action && a.action == "SET_WIFI"       && aOn != null && bOn != null && aOn != bOn) return true
        if (a.action == b.action && a.action == "SET_BLUETOOTH"  && aOn != null && bOn != null && aOn != bOn) return true
        return false
    }
}
