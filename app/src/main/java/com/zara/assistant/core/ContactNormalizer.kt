package com.zara.assistant.core

/**
 * Batch A1 — Fix 1 (Emoji/Symbol) + Fix 2 (Punctuation)
 *
 * FIX 1: Unicode category-based emoji and symbol removal.
 *   Covers: all emoji (Emoticons, Misc Symbols, Transport, Enclosed, etc.),
 *   pictographs, variation selectors (U+FE00–FE0F, U+E0100–E01EF),
 *   zero-width joiners (U+200D), enclosing keycaps, tags block.
 *   No hardcoded emoji — purely Unicode property/range based.
 *
 * FIX 2: General punctuation stripped.
 *   Covers: . , - _ : ; ' " ! ? ( ) [ ] / \ @ # $ % ^ & * + = ~ ` |
 */
object ContactNormalizer {

    /**
     * Strips emoji, pictographs, variation selectors, ZWJ, decorative symbols
     * and general punctuation. Then lowercases and collapses whitespace.
     */
    fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            val charCount = Character.charCount(cp)

            if (shouldStrip(cp)) {
                // skip
            } else {
                sb.appendCodePoint(cp)
            }
            i += charCount
        }
        // Collapse to lowercase words
        return sb.toString()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
    }

    private fun shouldStrip(cp: Int): Boolean {
        // ── Variation selectors (U+FE00–FE0F, variation selectors supplement U+E0100–E01EF) ──
        if (cp in 0xFE00..0xFE0F) return true
        if (cp in 0xE0100..0xE01EF) return true

        // ── Zero-width joiner and non-joiner ──
        if (cp == 0x200D || cp == 0x200C || cp == 0x200B) return true

        // ── Combining enclosing keycap U+20E3 ──
        if (cp == 0x20E3) return true

        // ── Miscellaneous Symbols (U+2600–U+26FF) ──
        if (cp in 0x2600..0x26FF) return true

        // ── Dingbats (U+2700–U+27BF) ──
        if (cp in 0x2700..0x27BF) return true

        // ── Supplemental Arrows, Misc Technical ──
        if (cp in 0x2300..0x23FF) return true

        // ── Enclosed Alphanumeric Supplement (U+1F100–U+1F1FF) ──
        if (cp in 0x1F100..0x1F1FF) return true

        // ── Enclosed Ideographic Supplement (U+1F200–U+1F2FF) ──
        if (cp in 0x1F200..0x1F2FF) return true

        // ── Miscellaneous Symbols and Pictographs (U+1F300–U+1F5FF) ──
        if (cp in 0x1F300..0x1F5FF) return true

        // ── Emoticons (U+1F600–U+1F64F) ──
        if (cp in 0x1F600..0x1F64F) return true

        // ── Transport and Map Symbols (U+1F680–U+1F6FF) ──
        if (cp in 0x1F680..0x1F6FF) return true

        // ── Supplemental Symbols and Pictographs (U+1F900–U+1F9FF) ──
        if (cp in 0x1F900..0x1F9FF) return true

        // ── Symbols and Pictographs Extended-A (U+1FA00–U+1FAFF) ──
        if (cp in 0x1FA00..0x1FAFF) return true

        // ── Tags block (U+E0000–U+E007F) ──
        if (cp in 0xE0000..0xE007F) return true

        // ── Unicode categories: Symbol Other (So), Symbol Modifier (Sk) ──
        val type = Character.getType(cp)
        if (type == Character.OTHER_SYMBOL.toInt()) return true
        if (type == Character.MODIFIER_SYMBOL.toInt()) return true

        // ── FIX 2: General punctuation characters ──
        if (cp in PUNCTUATION_CODEPOINTS) return true

        return false
    }

    private val PUNCTUATION_CODEPOINTS = setOf(
        '.'.code, ','.code, '-'.code, '_'.code, ':'.code, ';'.code,
        '\''.code, '"'.code, '!'.code, '?'.code, '('.code, ')'.code,
        '['.code, ']'.code, '{'.code, '}'.code, '/'.code, '\\'.code,
        '@'.code, '#'.code, '\$'.code, '%'.code, '^'.code, '&'.code,
        '*'.code, '+'.code, '='.code, '~'.code, '`'.code, '|'.code,
        '<'.code, '>'.code
    )

    fun normalizePhone(raw: String): String {
        var s = raw.filter { it.isDigit() || it == '+' }
        s = s.filter { it.isDigit() }
        return if (s.length > 10) s.takeLast(10) else s
    }
}
