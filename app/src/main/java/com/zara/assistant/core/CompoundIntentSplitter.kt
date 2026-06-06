package com.zara.assistant.core

/**
 * Layer 5.5 — Compound Intent Splitter.
 * Stateless, deterministic, O(n) string scan.
 * Splits on: " and ", " then ", " after that ", "&"
 * Respects quoted phrases (no split inside quotes).
 */
object CompoundIntentSplitter {

    private val DELIMITERS = listOf(" after that ", " then ", " and ", " & ", "&")

    fun split(input: String): List<String> {
        if (input.isBlank()) return listOf(input)

        // Skip splitting if entire input is quoted
        val trimmed = input.trim()
        if (trimmed.startsWith('"') && trimmed.endsWith('"')) return listOf(trimmed)

        val segments = mutableListOf<String>()
        var remaining = trimmed

        outer@ while (remaining.isNotEmpty()) {
            // Find earliest delimiter not inside quotes
            var bestIdx = -1
            var bestDelim = ""

            for (delim in DELIMITERS) {
                val idx = indexOfOutsideQuotes(remaining, delim)
                if (idx >= 0 && (bestIdx < 0 || idx < bestIdx)) {
                    bestIdx = idx
                    bestDelim = delim
                }
            }

            if (bestIdx < 0) {
                segments.add(remaining.trim())
                break
            }

            val before = remaining.substring(0, bestIdx).trim()
            if (before.isNotEmpty()) segments.add(before)
            remaining = remaining.substring(bestIdx + bestDelim.length)
        }

        return if (segments.isEmpty()) listOf(trimmed) else segments
    }

    private fun indexOfOutsideQuotes(text: String, delim: String): Int {
        var inQuote = false
        var i = 0
        while (i <= text.length - delim.length) {
            val ch = text[i]
            if (ch == '"') { inQuote = !inQuote; i++; continue }
            if (!inQuote && text.startsWith(delim, i)) return i
            i++
        }
        return -1
    }
}
