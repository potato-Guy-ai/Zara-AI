package com.zara.assistant.automation.nodes

import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.6D Batch 0.3 — NodeMatcher.
 *
 * Finds the best candidate node in a list of [ScannedNode]s by text,
 * contentDescription, or resourceId. Deterministic best-match scoring —
 * never just returns the first hit. No clicking, no actions.
 *
 * Scoring (simple by design, no AI/fuzzy logic):
 *   exact match (case-insensitive, trimmed)            -> 100
 *   partial match (one contains the other)             -> 70
 *   no match                                           -> 0
 */
object NodeMatcher {

    private const val SCORE_EXACT = 100
    private const val SCORE_PARTIAL = 70
    private const val SCORE_NONE = 0

    fun findByText(nodes: List<ScannedNode>, query: String): NodeMatch? =
        bestMatch(nodes, query, "text") { it.text }

    fun findByContentDescription(nodes: List<ScannedNode>, query: String): NodeMatch? =
        bestMatch(nodes, query, "contentDescription") { it.contentDescription }

    fun findByResourceId(nodes: List<ScannedNode>, query: String): NodeMatch? =
        bestMatch(nodes, query, "resourceId") { it.resourceId }

    private fun bestMatch(
        nodes: List<ScannedNode>,
        query: String,
        fieldLabel: String,
        selector: (ScannedNode) -> String?
    ): NodeMatch? {
        if (query.isBlank()) return null

        var best: NodeMatch? = null
        for (candidate in nodes) {
            val value = selector(candidate) ?: continue
            val score = scoreMatch(value, query)
            if (score > SCORE_NONE && (best == null || score > best.score)) {
                best = NodeMatch(candidate.node, score)
                if (score == SCORE_EXACT) break // can't beat an exact match
            }
        }

        if (best != null) {
            ZaraLogger.d("[NodeMatcher] match=$fieldLabel score=${best.score}")
        }
        return best
    }

    private fun scoreMatch(value: String, query: String): Int {
        val v = value.trim().lowercase()
        val q = query.trim().lowercase()
        if (v.isBlank() || q.isBlank()) return SCORE_NONE
        return when {
            v == q -> SCORE_EXACT
            v.contains(q) || q.contains(v) -> SCORE_PARTIAL
            else -> SCORE_NONE
        }
    }
}
