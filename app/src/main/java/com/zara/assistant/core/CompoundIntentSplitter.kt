package com.zara.assistant.core

/**
 * Layer 5.5 — Compound Intent Splitter.
 * Stateless, deterministic, O(n) string split only.
 */
object CompoundIntentSplitter {

    private val DELIMITERS = listOf(" and ", " then ", " after that ", " & ")

    /**
     * Split input into independent segments.
     * Returns single-element list if no delimiter found or input is quoted.
     */
    fun split(input: String): List<String> {
        // Do not split quoted phrases
        if (input.startsWith('"') && input.endsWith('"')) return listOf(input)

        var remaining = input
        val segments = mutableListOf<String>()

        while (remaining.isNotEmpty()) {
            val match = DELIMITERS
                .mapNotNull { d ->
                    val idx = remaining.indexOf(d, ignoreCase = true)
                    if (idx >= 0) idx to d else null
                }
                .minByOrNull { it.first }

            if (match == null) {
                segments.add(remaining.trim())
                break
            }

            val (idx, delim) = match
            val segment = remaining.substring(0, idx).trim()
            if (segment.isNotEmpty()) segments.add(segment)
            remaining = remaining.substring(idx + delim.length)
        }

        return if (segments.isEmpty()) listOf(input) else segments
    }
}
