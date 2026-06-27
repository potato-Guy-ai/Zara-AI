package com.zara.assistant.automation.nodes

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Layer 6.6D Batch 0.3 — ScannedNode.
 *
 * Lightweight snapshot of an AccessibilityNodeInfo's matchable fields,
 * captured once during traversal so NodeMatcher doesn't repeatedly call
 * into the (relatively expensive) AccessibilityNodeInfo getters. Still
 * holds the original [node] reference since later batches (click
 * automation) need it — this batch only reads/matches, never acts on it.
 */
data class ScannedNode(
    val node: AccessibilityNodeInfo,
    val text: String?,
    val contentDescription: String?,
    val resourceId: String?,
    val className: String?,
    val depth: Int
)
