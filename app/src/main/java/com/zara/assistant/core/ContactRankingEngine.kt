package com.zara.assistant.core

import com.zara.assistant.actions.ContactResolver

/**
 * Batch A1 — Fix 4 (Candidate Ranking)
 *
 * Ranking order:
 *   1. Exact normalized match     → score 100
 *   2. Starts-with match          → score 80
 *   3. Contains match             → score 60
 *   4. Fuzzy match (via ContactFuzzyMatcher) → score 10–50
 *
 * Only candidates with score > 0 are returned.
 * Fuzzy candidates included only when no exact/startsWith/contains candidates exist.
 */
object ContactRankingEngine {

    data class RankedContact(
        val contact: ContactResolver.ContactResult,
        val score: Int
    )

    fun rank(query: String, candidates: List<ContactResolver.ContactResult>): List<ContactResolver.ContactResult> {
        val normQuery = ContactNormalizer.normalize(query)

        val ranked = candidates.map { c ->
            val normName = ContactNormalizer.normalize(c.displayName)
            val score = when {
                normName == normQuery             -> 100
                normName.startsWith(normQuery)   -> 80
                normName.contains(normQuery)     -> 60
                else                             -> 0  // will try fuzzy below
            }
            RankedContact(c, score)
        }

        val highScored = ranked.filter { it.score > 0 }.sortedByDescending { it.score }
        if (highScored.isNotEmpty()) return highScored.map { it.contact }

        // No exact/startsWith/contains — try fuzzy
        return ContactFuzzyMatcher.match(query, candidates)
    }
}
