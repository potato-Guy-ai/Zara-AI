package com.zara.assistant.core

import com.zara.assistant.actions.ContactResolver

/**
 * Batch A1 — Fix 4 (Candidate Ranking)
 * Strong-candidate fix: scoreOf() exposed for EntityResolver use.
 *
 * Ranking order:
 *   1. Exact normalized match     → score 100
 *   2. Starts-with match          → score 80
 *   3. Contains match             → score 60
 *   4. Fuzzy match                → score 10–50
 *
 * STRONG_THRESHOLD = 80: candidates scoring >= 80 are "strong".
 * EntityResolver uses scoreOf() to compute per-candidate scores
 * before deciding auto-resolve vs clarification.
 */
object ContactRankingEngine {

    const val STRONG_THRESHOLD = 80

    data class RankedContact(
        val contact: ContactResolver.ContactResult,
        val score: Int
    )

    fun rank(query: String, candidates: List<ContactResolver.ContactResult>): List<ContactResolver.ContactResult> {
        val normQuery = ContactNormalizer.normalize(query)

        val ranked = candidates.map { c ->
            RankedContact(c, scoreOf(normQuery, ContactNormalizer.normalize(c.displayName)))
        }

        val highScored = ranked.filter { it.score > 0 }.sortedByDescending { it.score }
        if (highScored.isNotEmpty()) return highScored.map { it.contact }

        return ContactFuzzyMatcher.match(query, candidates)
    }

    /** Returns the deterministic score for a normalized name against a normalized query. */
    fun scoreOf(normQuery: String, normName: String): Int = when {
        normName == normQuery          -> 100
        normName.startsWith(normQuery) -> 80
        normName.contains(normQuery)   -> 60
        else                           -> 0
    }
}
