package com.zara.assistant.automation.nodes

import android.view.accessibility.AccessibilityNodeInfo
import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.6D Batch 0.3 — NodeScanner.
 *
 * Safely traverses an AccessibilityNodeInfo tree (DFS, iterative — no
 * recursion, so there's no call-stack risk regardless of tree shape) and
 * returns a bounded list of [ScannedNode] snapshots for NodeMatcher to
 * search. No clicking, no actions, no app-specific logic.
 *
 * Bounded by design — battery/perf safe on low-end devices:
 *   - maxDepth:  stop descending past this depth
 *   - maxNodes:  stop scanning once this many nodes are collected
 * Both default to conservative values and the traversal stops the moment
 * either limit is hit — no unbounded recursion, no full-tree guarantee.
 */
object NodeScanner {

    const val DEFAULT_MAX_DEPTH = 12
    const val DEFAULT_MAX_NODES = 300

    /**
     * Scans [root] and returns up to [maxNodes] nodes, never descending
     * past [maxDepth]. Returns an empty list if [root] is null — callers
     * (e.g. AccessibilityAutomationService.getRootNode()) may not always
     * have a root available.
     */
    fun scan(
        root: AccessibilityNodeInfo?,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        maxNodes: Int = DEFAULT_MAX_NODES
    ): List<ScannedNode> {
        if (root == null) return emptyList()

        val results = ArrayList<ScannedNode>(minOf(maxNodes, 64))
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)

        while (stack.isNotEmpty() && results.size < maxNodes) {
            val (node, depth) = stack.removeLast()

            results.add(
                ScannedNode(
                    node = node,
                    text = node.text?.toString(),
                    contentDescription = node.contentDescription?.toString(),
                    resourceId = node.viewIdResourceName,
                    className = node.className?.toString(),
                    depth = depth
                )
            )

            if (depth >= maxDepth) continue

            val childCount = node.childCount
            for (i in 0 until childCount) {
                if (results.size >= maxNodes) break
                val child = node.getChild(i) ?: continue
                stack.addLast(child to depth + 1)
            }
        }

        ZaraLogger.d("[NodeScanner] nodes scanned=${results.size}")
        return results
    }
}
