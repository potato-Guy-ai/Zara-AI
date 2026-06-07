package com.zara.assistant.core

/**
 * Layer 5 Hardening — Contact Normalizer.
 * Normalizes contact names and query strings before matching.
 * Never compare raw names — always compare normalized names.
 */
object ContactNormalizer {

    // Regex compiled once
    private val EMOJI_REGEX = Regex(
        "[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}" +
        "\\uD83C[\\uDF00-\\uDFFF]" +
        "|\\uD83D[\\uDC00-\\uDE4F]" +
        "|\\uD83D[\\uDE80-\\uDEFF]" +
        "|[\\u2600-\\u27BF]]"
    )
    private val DECORATION_REGEX = Regex("[!★•✦♥❤️♡♦♠♣⚡⭐*_\\-]+")

    fun normalize(raw: String): String {
        var s = raw
        s = EMOJI_REGEX.replace(s, "")       // remove emojis
        s = DECORATION_REGEX.replace(s, "")  // remove decorative symbols
        s = s.lowercase()
        s = s.split(" ").filter { it.isNotBlank() }.joinToString(" ")
        return s.trim()
    }

    fun normalizePhone(raw: String): String {
        // Strip spaces, dashes, parens, leading country codes for comparison
        var s = raw.filter { it.isDigit() || it == '+' }
        // Normalize +91... → last 10 digits for Indian numbers, etc.
        // General: keep last 10 digits as canonical
        s = s.filter { it.isDigit() }
        return if (s.length > 10) s.takeLast(10) else s
    }
}
