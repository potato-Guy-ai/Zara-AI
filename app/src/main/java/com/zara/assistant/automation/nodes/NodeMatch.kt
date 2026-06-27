package com.zara.assistant.automation.nodes

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Layer 6.6D Batch 0.3 — NodeMatch.
 * A matched node plus its confidence score (0-100). No automation
 * action is taken on [node] here — that belongs to a later batch.
 */
data class NodeMatch(
    val node: AccessibilityNodeInfo,
    val score: Int
)
