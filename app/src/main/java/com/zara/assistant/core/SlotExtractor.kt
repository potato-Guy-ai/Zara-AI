package com.zara.assistant.core

/**
 * Layer 4A/4B/4C — Slot Extraction.
 *
 * Stateless, pure, no Android dependencies, no coroutines, no background work.
 * Deterministic regex/rule-based extraction only.
 *
 * Rules:
 *   4B-1. APP extraction       — last " on <app>" pattern
 *   4B-2. CONTENT/QUERY        — media content or navigation/search query
 *   4B-3. DURATION             — timer duration normalized to seconds
 *   4C-1. RECIPIENT            — mirrors target for CALL / SEND_SMS / SEND_WHATSAPP
 *   4C-2. BODY preservation    — BODY forwarded unchanged if already present
 *   4C-3. Channel expansion    — CHANNEL normalized for telegram/signal/messenger/discord
 */
object SlotExtractor {

    // Compiled once — never per call.
    private val reDuration = Regex(
        "(\\d+(?:\\.\\d+)?)\\s*(hour|hr|minute|min|second|sec)s?",
        RegexOption.IGNORE_CASE
    )

    fun extract(intent: ZaraIntent): ZaraIntent {
        return when (intent.action) {
            IntentAction.PLAY_MUSIC   -> extractMedia(intent)
            IntentAction.NAVIGATE_TO  -> extractNavigation(intent)
            IntentAction.SET_TIMER    -> extractDuration(intent)
            IntentAction.CALL,
            IntentAction.SEND_SMS,
            IntentAction.SEND_WHATSAPP -> extractRecipientAndChannel(intent)
            else                       -> intent
        }
    }

    // ── 4B Rule 1+2 (media): "play believer on spotify" ──────────────────────
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

    // ── 4B Rule 1+2 (navigation): "navigate to airport on google maps" ────────
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

    // ── 4B Rule 3: duration normalized to seconds ───────────────────────────
    private fun extractDuration(intent: ZaraIntent): ZaraIntent {
        var totalSeconds = 0.0
        var found = false
        reDuration.findAll(intent.rawText).forEach { m ->
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

    // ── 4C Rule 1+2+3: recipient mirror + channel expansion ───────────────────
    private fun extractRecipientAndChannel(intent: ZaraIntent): ZaraIntent {
        val newExtra = intent.extra.toMutableMap()

        // Rule 1: mirror target into RECIPIENT
        val recipient = intent.target
        if (recipient != null && !newExtra.containsKey(IntentExtra.RECIPIENT)) {
            newExtra[IntentExtra.RECIPIENT] = recipient
        }

        // Rule 2: BODY preservation — no-op; BODY already in extra if present

        // Rule 3: channel expansion — normalize extended channels from rawText
        if (!newExtra.containsKey(IntentExtra.CHANNEL)) {
            val t = intent.rawText.lowercase()
            val channel = when {
                t.contains("telegram")  -> ChannelType.TELEGRAM
                t.contains("signal")    -> ChannelType.SIGNAL
                t.contains("messenger") -> ChannelType.MESSENGER
                t.contains("discord")   -> ChannelType.DISCORD
                else                   -> null
            }
            if (channel != null) newExtra[IntentExtra.CHANNEL] = channel
        }

        return intent.copy(extra = newExtra)
    }
}
