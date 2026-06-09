package com.zara.assistant.core

import com.zara.assistant.actions.ContactResolver

/**
 * Batch A1 — Fix 3 (Fuzzy Matching) + Fix 4 (Ranking)
 *
 * Lightweight deterministic fuzzy contact matching.
 * No ML, no embeddings, no cloud.
 *
 * Algorithm: Levenshtein distance on normalized names.
 * Threshold: distance <= max(2, floor(queryLen * 0.4)) to catch
 *   madan→mathan (distance 2), malvin→malveen (distance 2), etc.
 *
 * Also handles token-level partial matching:
 *   "madan" matches token "mathan" in "mathan mama".
 */
object ContactFuzzyMatcher {

    private const val MAX_DISTANCE_RATIO = 0.4  // up to 40% of query length
    private const val MIN_ABSOLUTE_THRESHOLD = 1
    private const val MAX_ABSOLUTE_THRESHOLD = 3

    data class FuzzyMatch(
        val contact: ContactResolver.ContactResult,
        val score: Int  // 0–100, higher = better
    )

    /**
     * Returns all candidates that fuzzy-match the query, sorted by score descending.
     * Score range 10–50 (below exact/startsWith/contains scores in ContactRankingEngine).
     */
    fun match(query: String, candidates: List<ContactResolver.ContactResult>): List<ContactResolver.ContactResult> {
        val normQuery = ContactNormalizer.normalize(query)
        if (normQuery.isBlank()) return emptyList()

        val threshold = computeThreshold(normQuery.length)
        val queryTokens = normQuery.split(" ").filter { it.isNotBlank() }

        return candidates
            .mapNotNull { contact ->
                val normName = ContactNormalizer.normalize(contact.displayName)
                val nameTokens = normName.split(" ").filter { it.isNotBlank() }

                val score = computeBestScore(normQuery, queryTokens, normName, nameTokens, threshold)
                if (score > 0) FuzzyMatch(contact, score) else null
            }
            .sortedByDescending { it.score }
            .map { it.contact }
    }

    private fun computeBestScore(
        normQuery: String,
        queryTokens: List<String>,
        normName: String,
        nameTokens: List<String>,
        threshold: Int
    ): Int {
        // Full-name distance
        val fullDist = levenshtein(normQuery, normName)
        if (fullDist <= threshold) {
            return scoreFromDistance(fullDist, normQuery.length)
        }

        // Token-level: each query token vs each name token
        var bestTokenScore = 0
        for (qt in queryTokens) {
            val tThreshold = computeThreshold(qt.length)
            for (nt in nameTokens) {
                val d = levenshtein(qt, nt)
                if (d <= tThreshold) {
                    val s = scoreFromDistance(d, qt.length)
                    if (s > bestTokenScore) bestTokenScore = s
                }
            }
        }
        return bestTokenScore
    }

    /**
     * Maps edit distance to a score in range [10, 50].
     * distance=0 → 50 (but this case is handled by exact match upstream)
     * distance=1 → 40
     * distance=2 → 30
     * distance=3 → 20
     */
    private fun scoreFromDistance(distance: Int, queryLen: Int): Int = when (distance) {
        0 -> 50
        1 -> 40
        2 -> 30
        3 -> 20
        else -> 10
    }

    private fun computeThreshold(len: Int): Int {
        val ratio = (len * MAX_DISTANCE_RATIO).toInt()
        return ratio.coerceIn(MIN_ABSOLUTE_THRESHOLD, MAX_ABSOLUTE_THRESHOLD)
    }

    /**
     * Standard iterative Levenshtein distance.
     * O(m*n) time, O(min(m,n)) space.
     */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val (shorter, longer) = if (a.length <= b.length) a to b else b to a
        var prev = IntArray(shorter.length + 1) { it }
        var curr = IntArray(shorter.length + 1)

        for (i in 1..longer.length) {
            curr[0] = i
            for (j in 1..shorter.length) {
                val cost = if (longer[i - 1] == shorter[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[shorter.length]
    }
}
