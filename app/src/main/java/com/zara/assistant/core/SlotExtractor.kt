package com.zara.assistant.core

/**
 * Layer 4B — Core Slot Extraction.
 *
 * Stateless, pure, no Android dependencies, no coroutines, no background work.
 * Deterministic regex/rule-based extraction only.
 *
 * Rules implemented:
 *   1. APP extraction  — last " on <app>" pattern
 *   2. CONTENT/QUERY   — media content or navigation/search query
 *   3. DURATION        — timer duration normalized to seconds
 */
object SlotExtractor {

    // Compiled once — never per call.
    private val reOnApp = Regex("^(.+)\\s+on\\s+([\\w\\s]+?)\\s*$", RegexOption.IGNORE_CASE)
    private val reDuration = Regex(
        "(\\d+(?:\\.\\d+)?)\\s*(hour|hr|minute|min|second|sec)s?",
        RegexOption.IGNORE_CASE
    )

    fun extract(intent: ZaraIntent): ZaraIntent {
        return when (intent.action) {
            IntentAction.PLAY_MUSIC  -> extractMedia(intent)
            IntentAction.NAVIGATE_TO -> extractNavigation(intent)
            IntentAction.SET_TIMER   -> extractDuration(intent)
            else                     -> intent
        }
    }

    // ── Rule 1 + 2 (media): "play believer on spotify" ───────────────────────────
    // Uses LAST occurrence of " on " to handle "play on my way on spotify" correctly.
    private fun extractMedia(intent: ZaraIntent): ZaraIntent {
        val raw = intent.target ?: return intent
        val lastOnIdx = raw.lastIndexOf(" on ", ignoreCase = true)
        if (lastOnIdx < 0) return intent

        val content = raw.substring(0, lastOnIdx).trim()
        val app     = raw.substring(lastOnIdx + 4).trim()

        if (content.isEmpty() || app.isEmpty()) return intent

        val newExtra = intent.extra.toMutableMap()
        newExtra[IntentExtra.CONTENT] = content
        newExtra[IntentExtra.APP]     = app
        return intent.copy(extra = newExtra)
    }

    // ── Rule 1 + 2 (navigation): "navigate to airport on google maps" ────────────
    private fun extractNavigation(intent: ZaraIntent): ZaraIntent {
        val raw = intent.target ?: return intent
        val lastOnIdx = raw.lastIndexOf(" on ", ignoreCase = true)
        if (lastOnIdx < 0) return intent

        val query = raw.substring(0, lastOnIdx).trim()
        val app   = raw.substring(lastOnIdx + 4).trim()

        if (query.isEmpty() || app.isEmpty()) return intent

        val newExtra = intent.extra.toMutableMap()
        newExtra[IntentExtra.QUERY] = query
        newExtra[IntentExtra.APP]   = app
        return intent.copy(extra = newExtra)
    }

    // ── Rule 3: duration normalized to seconds ─────────────────────────────────
    private fun extractDuration(intent: ZaraIntent): ZaraIntent {
        val raw = intent.rawText
        var totalSeconds = 0.0
        var found = false

        reDuration.findAll(raw).forEach { m ->
            val value = m.groupValues[1].toDoubleOrNull() ?: return@forEach
            val unit  = m.groupValues[2].lowercase()
            totalSeconds += when {
                unit.startsWith("hour") || unit.startsWith("hr") -> value * 3600
                unit.startsWith("min")                           -> value * 60
                else                                             -> value
            }
            found = true
        }

        if (!found) return intent

        val newExtra = intent.extra.toMutableMap()
        newExtra[IntentExtra.DURATION] = totalSeconds.toLong().toString()
        return intent.copy(extra = newExtra)
    }
}
