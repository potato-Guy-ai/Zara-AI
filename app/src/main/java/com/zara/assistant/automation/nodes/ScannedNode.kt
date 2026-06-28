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
 *
 * Batch 0.3A — Node lifetime policy (documentation only, no functional
 * change):
 *   - [node] is a SHORT-LIVED reference into the live accessibility tree
 *     for the window that was active at scan time.
 *   - It must be used immediately (within the same scan/match/act cycle)
 *     or discarded. Do NOT hold onto it past that.
 *   - It must NEVER be persisted to disk or any long-lived store.
 *   - It must NEVER be cached across AutomationSessions — a new scan is
 *     required for each session/turn; nodes from a prior session are not
 *     valid inputs to a new one.
 *   - Before any future action execution (click, etc., later batches)
 *     acts on [node], that code must validate the node is still fresh
 *     (e.g. AccessibilityNodeInfo.refresh() / isVisibleToUser, or by
 *     re-scanning) rather than assuming a node captured earlier is still
 *     valid — the underlying view may have been recycled or removed.
 */
data class ScannedNode(
    val node: AccessibilityNodeInfo,
    val text: String?,
    val contentDescription: String?,
    val resourceId: String?,
    val className: String?,
    val depth: Int
)
