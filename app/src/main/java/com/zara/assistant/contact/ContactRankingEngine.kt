package com.zara.assistant.contact

/**
 * Scores a contact display name against a normalized query.
 *
 * Scoring tiers:
 *   100 — exact match          ("atha"  vs "atha")
 *    80 — starts-with match    ("atha 2" starts with "atha")
 *    60 — contains match       ("periatha" contains "atha")
 *     0 — no match
 *
 * Scores are intentionally fixed; do NOT alter thresholds here.
 */
object ContactRankingEngine {

    data class RankedContact(
        val displayName: String,
        val score: Int
    )

    fun rank(query: String, contacts: List<String>): List<RankedContact> {
        val q = ContactNormalizer.normalize(query)
        return contacts
            .map { name ->
                val n = ContactNormalizer.normalize(name)
                val score = when {
                    n == q          -> 100
                    n.startsWith(q) -> 80
                    n.contains(q)   -> 60
                    else            -> 0
                }
                RankedContact(displayName = name, score = score)
            }
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
    }
}
