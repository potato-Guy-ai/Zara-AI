package com.zara.assistant.core

/**
 * Layer 5 Hardening — Preferred App Registry.
 * Maps user-spoken app names to canonical preferences.
 * Prevents e.g. "youtube" → YouTube Music.
 */
object PreferredAppRegistry {

    // Maps normalized user query → preferred app label (used to bias app cache lookup)
    private val preferences = mapOf(
        "youtube"       to "youtube",
        "music"         to "spotify",
        "spotify"       to "spotify",
        "browser"       to "chrome",
        "maps"          to "google maps",
        "google maps"   to "google maps",
        "whatsapp"      to "whatsapp",
        "instagram"     to "instagram",
        "telegram"      to "telegram",
        "chrome"        to "chrome",
        "gmail"         to "gmail",
        "camera"        to "camera",
        "calculator"    to "calculator",
        "settings"      to "settings",
        "clock"         to "clock",
        "calendar"      to "calendar"
    )

    /** Returns canonical preferred label for lookup, or the original query. */
    fun preferred(query: String): String =
        preferences[query.lowercase().trim()] ?: query.lowercase().trim()
}
