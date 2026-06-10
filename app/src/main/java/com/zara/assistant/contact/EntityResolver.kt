package com.zara.assistant.contact

/**
 * Resolves a contact query to either a single auto-resolved contact
 * or a clarification list.
 *
 * AMBIGUITY RULES
 * ───────────────
 * Strong candidate threshold : score >= 80
 *
 * If strong candidate count > 1  →  trigger clarification.
 *   Clarification list contains ONLY strong candidates (score >= 80).
 *   Lower-tier candidates (e.g. score 60) are excluded from the list.
 *
 * If strong candidate count == 1 →  auto resolve to that candidate.
 * If only one ranked result exists →  auto resolve.
 * If single exact match exists and no other strong candidate →  auto resolve.
 *
 * Numeric suffix contacts are treated as distinct, valid contacts.
 *   Query "atha"   → Atha (100) + Atha 2 (80) → clarification.
 *   Query "atha 2" → Atha 2 (100) exact match, only strong → auto resolve.
 */
object EntityResolver {

    private const val STRONG_THRESHOLD = 80

    sealed class ResolveResult {
        /** Unambiguous: proceed with this contact. */
        data class Resolved(val displayName: String) : ResolveResult()

        /** Ambiguous: ask the user to pick from this list. */
        data class NeedsClarity(val candidates: List<String>) : ResolveResult()

        /** No contacts matched the query at all. */
        object NoMatch : ResolveResult()
    }

    fun resolve(
        query: String,
        contacts: List<String>
    ): ResolveResult {
        val ranked = ContactRankingEngine.rank(query, contacts)

        if (ranked.isEmpty()) return ResolveResult.NoMatch

        // Single result — always auto resolve regardless of score.
        if (ranked.size == 1) return ResolveResult.Resolved(ranked[0].displayName)

        // Collect strong candidates (score >= 80).
        val strong = ranked.filter { it.score >= STRONG_THRESHOLD }

        return when {
            // Multiple strong candidates → clarification required.
            // Only strong candidates are shown; lower-tier entries are hidden.
            strong.size > 1 -> ResolveResult.NeedsClarity(
                candidates = strong.map { it.displayName }
            )

            // Exactly one strong candidate → auto resolve.
            strong.size == 1 -> ResolveResult.Resolved(strong[0].displayName)

            // No strong candidates but multiple weak results → clarification
            // with all ranked results (edge case; preserves safety).
            else -> ResolveResult.NeedsClarity(
                candidates = ranked.map { it.displayName }
            )
        }
    }
}
