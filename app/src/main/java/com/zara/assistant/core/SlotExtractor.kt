package com.zara.assistant.core

/**
 * Layer 4A/4B/4C + Batch B1 + B2.2
 *
 * B2.2: Added extractAlarmTime for SET_ALARM.
 *   Parses: "6 am", "7:30 am", "8 pm", "5" (hour only)
 *   Stores ALARM_HOUR and ALARM_MINUTE in extras.
 */
object SlotExtractor {

    private val reDuration = Regex(
        "(\\d+(?:\\.\\d+)?)\\s*(hour|hr|minute|min|second|sec)s?",
        RegexOption.IGNORE_CASE
    )

    // B2.2: alarm time patterns
    // Matches: "6 am", "6am", "7:30 am", "7:30am", "17:00", "5"
    private val reAlarmTime = Regex(
        "(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?",
        RegexOption.IGNORE_CASE
    )

    fun extract(intent: ZaraIntent): ZaraIntent {
        return when (intent.action) {
            IntentAction.PLAY_MUSIC    -> extractMedia(intent)
            IntentAction.NAVIGATE_TO   -> extractNavigation(intent)
            IntentAction.SET_TIMER     -> extractDuration(intent)
            IntentAction.SET_ALARM     -> extractAlarmTime(intent)  // B2.2
            IntentAction.SEARCH_QUERY  -> extractSearch(intent)
            IntentAction.CALL,
            IntentAction.SEND_SMS,
            IntentAction.SEND_WHATSAPP -> extractRecipientAndChannel(intent)
            else                       -> intent
        }
    }

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

    // B2.2: extract alarm hour/minute from rawText
    private fun extractAlarmTime(intent: ZaraIntent): ZaraIntent {
        val raw = intent.rawText.lowercase()
        val m = reAlarmTime.find(raw) ?: return intent
        var hour   = m.groupValues[1].toIntOrNull() ?: return intent
        val minute = m.groupValues[2].toIntOrNull() ?: 0
        val ampm   = m.groupValues[3].lowercase()
        when {
            ampm == "pm" && hour < 12 -> hour += 12
            ampm == "am" && hour == 12 -> hour = 0
            // no am/pm and hour <= 12: assume as-spoken (e.g. "5" = 5 AM or 5 PM ambiguous — keep as-is)
        }
        val newExtra = intent.extra.toMutableMap()
        newExtra[IntentExtra.ALARM_HOUR]   = hour.toString()
        newExtra[IntentExtra.ALARM_MINUTE] = minute.toString()
        return intent.copy(extra = newExtra)
    }

    private fun extractSearch(intent: ZaraIntent): ZaraIntent {
        val raw = intent.target ?: return intent
        val lastOnIdx = raw.lastIndexOf(" on ", ignoreCase = true)
        val newExtra = intent.extra.toMutableMap()
        if (lastOnIdx >= 0) {
            val query = raw.substring(0, lastOnIdx).trim()
            val app   = raw.substring(lastOnIdx + 4).trim()
            if (query.isNotEmpty()) newExtra[IntentExtra.QUERY] = query
            if (app.isNotEmpty())   newExtra[IntentExtra.APP]   = app
        } else {
            if (raw.isNotEmpty()) newExtra[IntentExtra.QUERY] = raw
        }
        return intent.copy(extra = newExtra)
    }

    private fun extractRecipientAndChannel(intent: ZaraIntent): ZaraIntent {
        val newExtra = intent.extra.toMutableMap()
        val recipient = intent.target
        if (recipient != null && !newExtra.containsKey(IntentExtra.RECIPIENT)) {
            newExtra[IntentExtra.RECIPIENT] = recipient
        }
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
