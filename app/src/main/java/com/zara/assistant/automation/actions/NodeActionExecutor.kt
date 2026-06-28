package com.zara.assistant.automation.actions

import android.view.accessibility.AccessibilityNodeInfo
import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.6D Batch 0.4 — NodeActionExecutor.
 *
 * The first batch that performs real UI interaction. Single public
 * function: click(node). No gestures, no coordinates, no retries, no
 * scrolling, no async logic — a single deterministic ACTION_CLICK attempt.
 *
 * Flow: validate node -> walk up to nearest clickable ancestor (bounded) ->
 * performAction(ACTION_CLICK) -> structured result.
 */
object NodeActionExecutor {

    private const val MAX_ANCESTOR_DEPTH = 6

    fun click(node: AccessibilityNodeInfo?): NodeActionResult {
        if (node == null || !node.isEnabled) {
            ZaraLogger.d("[NodeAction] failed invalid node")
            return NodeActionResult(NodeActionStatus.FAILED_INVALID_NODE, "node is null or disabled")
        }

        val target = findClickableAncestor(node)
        if (target == null) {
            ZaraLogger.d("[NodeAction] failed not clickable")
            return NodeActionResult(NodeActionStatus.FAILED_NOT_CLICKABLE, "no clickable ancestor found")
        }

        val performed = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return if (performed) {
            ZaraLogger.d("[NodeAction] click success")
            NodeActionResult(NodeActionStatus.SUCCESS)
        } else {
            ZaraLogger.d("[NodeAction] failed action failed")
            NodeActionResult(NodeActionStatus.FAILED_ACTION_FAILED, "performAction(ACTION_CLICK) returned false")
        }
    }

    /**
     * Walks from [node] upward (node itself included) looking for the
     * nearest clickable ancestor. Bounded by MAX_ANCESTOR_DEPTH — stops
     * if the limit is reached or a parent is null.
     */
    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth <= MAX_ANCESTOR_DEPTH) {
            if (current.isClickable) return current
            current = current.parent
            depth++
        }
        return null
    }
}
