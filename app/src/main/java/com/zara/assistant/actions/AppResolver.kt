package com.zara.assistant.actions

/**
 * Result from AppResolver.resolve().
 * source field allows callers to log/rank resolution quality.
 * candidates is populated when clarification is needed.
 */
data class AppResolveResult(
    val packageName: String?,
    val displayLabel: String?,
    val confidence: Float,
    val candidates: List<String> = emptyList(),
    val source: ResolveSource = ResolveSource.NONE
)

enum class ResolveSource { EXACT, ALIAS, STARTS_WITH, CONTAINS, FUZZY, NONE }

/**
 * Single entry point for all app resolution logic.
 * Phase 1: RuleBasedAppResolver (alias + fuzzy)
 * Phase 3: swap in MiniLmAppResolver via same interface, no AppActions changes needed.
 */
interface AppResolver {
    fun resolve(query: String, cache: Map<String, String>): AppResolveResult
}

/**
 * Provides curated alias mappings.
 * Structured so UserAliasProvider can be added in a future phase
 * (e.g. load from DataStore) without touching RuleBasedAppResolver.
 */
interface AliasProvider {
    /** Returns canonical app label for [query], or null if no alias exists. */
    fun resolve(query: String): String?
}

/** Built-in curated aliases only. No user data, no persistence. */
class BuiltInAliasProvider : AliasProvider {
    private val aliases: Map<String, String> = mapOf(
        // Social
        "ig"          to "instagram",
        "insta"       to "instagram",
        "wa"          to "whatsapp",
        "fb"          to "facebook",
        "tg"          to "telegram",
        "snap"        to "snapchat",
        "tt"          to "tiktok",
        // Video / Music
        "yt"          to "youtube",
        "ytm"         to "youtube music",
        "yt music"    to "youtube music",
        "spotify"     to "spotify",   // exact, but alias ensures consistent casing
        // Games
        "ff"          to "free fire",
        "ff max"      to "free fire max",
        "bgmi"        to "battlegrounds mobile india",
        "pubg"        to "pubg mobile",
        "cod"         to "call of duty",
        // Productivity / System
        "gm"          to "gmail",
        "yt studio"   to "youtube studio",
        "maps"        to "google maps",
        "drive"       to "google drive",
        "docs"        to "google docs",
        "sheets"      to "google sheets",
        "meet"        to "google meet",
        "pay"         to "google pay",
        "gpay"        to "google pay",
        "phonepe"     to "phonepe",
        "swiggy"      to "swiggy",
        "zomato"      to "zomato",
        "ola"         to "ola",
        "uber"        to "uber"
    )

    override fun resolve(query: String): String? = aliases[query.lowercase().trim()]
}

/**
 * Phase 1 rule-based resolver.
 * Matching order:
 *   1. Exact        (confidence 1.0)
 *   2. Alias        (confidence 0.95)
 *   3. StartsWith   (confidence 0.85)
 *   4. Contains     (confidence 0.75)
 *   5. Fuzzy        (confidence 0.7, only query.length >= 4)
 *   6. Clarification / None
 *
 * Future: inject MiniLmAppResolver as [semanticFallback] for Phase 3
 * without modifying this class.
 */
class RuleBasedAppResolver(
    private val aliasProvider: AliasProvider = BuiltInAliasProvider(),
    // Phase 3 hook: set to a MiniLmAppResolver instance when ready
    private val semanticFallback: AppResolver? = null
) : AppResolver {

    override fun resolve(query: String, cache: Map<String, String>): AppResolveResult {
        val q = query.lowercase().trim()

        // 1. Exact
        cache[q]?.let { return AppResolveResult(it, q, 1.0f, source = ResolveSource.EXACT) }

        // 2. Alias → re-run exact + startsWith + contains on resolved label
        aliasProvider.resolve(q)?.let { resolved ->
            cache[resolved]?.let {
                return AppResolveResult(it, resolved, 0.95f, source = ResolveSource.ALIAS)
            }
            // Alias resolved but label not in cache (app not installed)
            // Fall through to string matching with resolved name
            val sw = cache.entries.filter { e -> e.key.startsWith(resolved) }
            if (sw.size == 1) return AppResolveResult(sw[0].value, sw[0].key, 0.9f, source = ResolveSource.ALIAS)
            val ct = cache.entries.filter { e -> e.key.contains(resolved) }
            if (ct.size == 1) return AppResolveResult(ct[0].value, ct[0].key, 0.85f, source = ResolveSource.ALIAS)
        }

        // 3. StartsWith
        val sw = cache.entries.filter { it.key.startsWith(q) }
        if (sw.size == 1) return AppResolveResult(sw[0].value, sw[0].key, 0.85f, source = ResolveSource.STARTS_WITH)
        if (sw.size > 1)  return AppResolveResult(null, null, 0f,
            candidates = sw.map { it.key }, source = ResolveSource.STARTS_WITH)

        // 4. Contains
        val ct = cache.entries.filter { it.key.contains(q) }
        if (ct.size == 1) return AppResolveResult(ct[0].value, ct[0].key, 0.75f, source = ResolveSource.CONTAINS)
        if (ct.size > 1) {
            val best = ct.minByOrNull { it.key.length }
            if (best != null && best.key.split(" ").size <= 2)
                return AppResolveResult(best.value, best.key, 0.75f, source = ResolveSource.CONTAINS)
            return AppResolveResult(null, null, 0f,
                candidates = ct.map { it.key }, source = ResolveSource.CONTAINS)
        }

        // 5. Fuzzy — only for query.length >= 4 (prevents false positives on short inputs)
        if (q.length >= 4) {
            val fuzzyHits = cache.entries
                .map { it to levenshtein(q, it.key) }
                .filter { (_, d) -> d <= 2 }
                .sortedBy { (_, d) -> d }
            if (fuzzyHits.size == 1)
                return AppResolveResult(fuzzyHits[0].first.value, fuzzyHits[0].first.key,
                    0.7f, source = ResolveSource.FUZZY)
            if (fuzzyHits.size > 1)
                return AppResolveResult(null, null, 0f,
                    candidates = fuzzyHits.take(3).map { it.first.key },
                    source = ResolveSource.FUZZY)
        }

        // 6. Semantic fallback (Phase 3 MiniLM hook)
        semanticFallback?.let { return it.resolve(query, cache) }

        return AppResolveResult(null, null, 0f, source = ResolveSource.NONE)
    }

    /** Iterative Levenshtein — O(n*m), no allocations beyond two int arrays. */
    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j-1] + 1,
                    prev + if (a[i-1] == b[j-1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
