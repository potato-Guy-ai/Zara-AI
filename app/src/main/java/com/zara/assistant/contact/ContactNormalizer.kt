package com.zara.assistant.contact

/**
 * Normalizes contact display names for comparison.
 * Lowercases and trims only. Does NOT strip numeric suffixes.
 * "Atha 2" normalizes to "atha 2" — preserving numeric identity.
 */
object ContactNormalizer {

    fun normalize(name: String): String {
        return name.trim().lowercase()
    }
}
