package com.zara.assistant.core

import com.zara.assistant.actions.ContactResolver

/**
 * Layer 5 Hardening — Contact Ranking Engine.
 * Scores and sorts candidates by match quality.
 */
object ContactRankingEngine {

    data class RankedContact(
        val contact: ContactResolver.ContactResult,
        val score: Int
    )

    fun rank(query: String, candidates: List<ContactResolver.ContactResult>): List<ContactResolver.ContactResult> {
        val normQuery = ContactNormalizer.normalize(query)
        return candidates
            .map { c ->
                val normName = ContactNormalizer.normalize(c.displayName)
                val score = when {
                    normName == normQuery                -> 100
                    normName.startsWith(normQuery)      -> 80
                    normName.contains(normQuery)        -> 60
                    else                                -> 20
                }
                RankedContact(c, score)
            }
            .sortedByDescending { it.score }
            .map { it.contact }
    }
}
