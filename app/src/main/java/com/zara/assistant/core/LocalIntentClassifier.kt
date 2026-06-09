package com.zara.assistant.core

/**
 * Offline intent classifier.
 *
 * Design principles:
 * - All Regex compiled once — never per call.
 * - Action intents require BOTH a verb AND a clear action target context.
 * - Knowledge questions ("how do I call...", "explain bluetooth") must NOT
 *   trigger device actions. Confidence gating prevents false positives.
 * - Channel (SMS vs WhatsApp) is extracted and preserved.
 * - Phrase normalization handles varied call/message phrasings.
 *
 * B1: Added SEARCH_QUERY intent for standalone search commands.
 */
class LocalIntentClassifier {

    // ── Knowledge-question guard (checked FIRST) ───────────────────────────
    private val reKnowledge = Regex(
        ".*(how (do|does|can|would|to)|what is|what are|explain|tell me about|" +
        "describe|difference between|meaning of|definition of|understand).*"
    )

    // ── Call intent ────────────────────────────────────────────────────────
    private val reCallAction = Regex(
        "(?:call|dial|phone|ring|make (?:a )?call (?:to|for))\\s+(.+)"
    )
    private val reAnswerCall = Regex(".*(answer|pick up).*(call).*")
    private val reEndCall    = Regex(".*(hang up|end call|end the call|reject call|disconnect).*")

    // ── Message intent ─────────────────────────────────────────────────────
    private val reWhatsappChannel = Regex(".*whatsapp.*")
    private val reSmsChannel      = Regex(".*(send sms|send a text|text message|sms to).*")
    private val reMsgVerb    = Regex(
        "(?:message|text|tell|whatsapp|msg|send(?: (?:sms|a text|whatsapp(?: message)?))?(?: to)?)\\s+([\\w\\s]+?)\\s+(?:saying|that|with message|with body|that says|:)\\s*(.*)",
        RegexOption.IGNORE_CASE
    )
    private val reMsgFallback = Regex(
        "(?:message|text|whatsapp|msg)\\s+(.+)"
    )
    private val reMessageTrigger = Regex(
        ".*(message|send|text|whatsapp|msg|tell).*(amma|anna|\\w+).*"
    )

    // ── App launch ─────────────────────────────────────────────────────────
    private val reOpenVerb = Regex(
        "(?:open|launch|start|switch to|go to app)\\s+(.+)"
    )
    private val reOpenTrigger = Regex(".*(open|launch|start|switch to|go to app).*")

    // ── B1: Search intent ──────────────────────────────────────────────────
    // Matches: "search cats", "search for cats", "search cats on youtube",
    //          "find cats", "look up cats", "look for cats"
    private val reSearch = Regex(
        "(?:search(?: for)?|find|look(?:ing)? (?:up|for))\\s+(.+)"
    )
    private val reSearchTrigger = Regex(
        ".*(search(?: for)?|^find |look(?:ing)? (?:up|for)).*"
    )

    // ── Device controls ────────────────────────────────────────────────────
    private val reWifi        = Regex(".*(wi.?fi).*")
    private val reBluetooth   = Regex(".*(bluetooth|bt).*")
    private val reFlashlight  = Regex(".*(flashlight|torch).*")
    private val reVolume      = Regex(".*(volume|vol).*(up|down|max|low|raise|lower|increase|decrease).*")
    private val reLock        = Regex(".*(lock).*(phone|screen|device).*")
    private val reCamera      = Regex(".*(take photo|take picture|open camera|selfie|capture).*")
    private val reAlarm       = Regex(".*(set|create|add).*(alarm).*")
    private val reTimer       = Regex(".*(set|start|create).*(timer).*")
    private val reNavigate    = Regex(".*(navigate to|directions to|take me to|drive to)\\s+(.+)")
    private val rePlay        = Regex("(?:play|listen to)\\s+(.+)")
    private val rePlayTrigger = Regex(".*(play|listen to).*")

    // ── Sound mode ─────────────────────────────────────────────────────────
    private val reSilentOn  = Regex(
        ".*(turn on silent|enable silent|silent mode|put on silent|do not disturb|dnd|^mute$|^mute everything$).*"
    )
    private val reSilentOff = Regex(
        ".*(turn off silent|disable silent|normal mode|ring mode|ringer on|unmute).*"
    )
    private val reVibrate   = Regex(".*(vibrate mode|enable vibrate|turn on vibrate|set to vibrate).*")

    // ── Toggle helpers ─────────────────────────────────────────────────────
    private val reOffKeyword = Regex(".*(turn off|switch off|disable|deactivate).*")
    private val reOnKeyword  = Regex(".*(turn on|switch on|enable|activate).*")

    // ── Conversation ───────────────────────────────────────────────────────
    private val reTime     = Regex(".*(what.?s the time|what time|current time|time now|time is it).*")
    private val reDate     = Regex(".*(what.?s the date|what date|today.?s date|what day|day is it).*")
    private val reGreeting = Regex(".*(how are you|you okay|you good|hey zara|hello zara|hi zara).*")
    private val reStop     = Regex(".*(stop listening|go to sleep|goodbye|bye zara|shut up|cancel|never mind).*")

    // ══════════════════════════════════════════════════════════════════════
    // Public API
    // ══════════════════════════════════════════════════════════════════════

    fun classify(text: String): ZaraIntent {
        if (text.isBlank()) return unknown(text)
        val t = text.lowercase().trim()

        // ── 1. Knowledge guard — must run FIRST ──────────────────────────
        if (reKnowledge.matches(t)) {
            return cloudIntent(text)
        }

        // ── 2. Call ──────────────────────────────────────────────────────
        if (reAnswerCall.matches(t)) return action(IntentAction.ANSWER_CALL, text)
        if (reEndCall.matches(t))    return action(IntentAction.END_CALL, text)
        reCallAction.find(t)?.let { m ->
            val target = m.groupValues[1].trim()
            if (target.isNotBlank()) return action(IntentAction.CALL, text, target = target)
        }

        // ── 3. Messaging (with channel detection) ────────────────────────
        if (isMessageIntent(t)) return messageIntent(t, text)

        // ── 4. Navigation ────────────────────────────────────────────────
        reNavigate.find(t)?.let { m ->
            val dest = m.groupValues[2].trim()
            if (dest.isNotBlank()) return action(IntentAction.NAVIGATE_TO, text, target = dest)
        }

        // ── 5. Music ─────────────────────────────────────────────────────
        rePlay.find(t)?.let { m ->
            val query = m.groupValues[1].trim()
            if (query.isNotBlank()) return action(IntentAction.PLAY_MUSIC, text, target = query)
        }

        // ── 6. App launch ────────────────────────────────────────────────
        if (reOpenTrigger.matches(t)) {
            reOpenVerb.find(t)?.let { m ->
                val appName = m.groupValues[1].trim()
                if (appName.isNotBlank()) return action(IntentAction.OPEN_APP, text, target = appName)
            }
        }

        // ── 7. Camera shortcut ───────────────────────────────────────────
        if (reCamera.matches(t)) return action(IntentAction.OPEN_CAMERA, text)

        // ── 8. Alarms / Timers ───────────────────────────────────────────
        if (reAlarm.matches(t)) return action(IntentAction.SET_ALARM, text)
        if (reTimer.matches(t)) return action(IntentAction.SET_TIMER, text)

        // ── 9. Lock ──────────────────────────────────────────────────────
        if (reLock.matches(t)) return action(IntentAction.LOCK_SCREEN, text)

        // ── 10. Sound mode ───────────────────────────────────────────────
        if (reSilentOff.matches(t)) return action(IntentAction.SET_SILENT, text,
            extra = mapOf(IntentExtra.ON to "false", IntentExtra.MODE to "normal"))
        if (reVibrate.matches(t))   return action(IntentAction.SET_SILENT, text,
            extra = mapOf(IntentExtra.ON to "true", IntentExtra.MODE to "vibrate"))
        if (reSilentOn.matches(t))  return action(IntentAction.SET_SILENT, text,
            extra = mapOf(IntentExtra.ON to "true", IntentExtra.MODE to "silent"))

        // ── 11. Device toggles ───────────────────────────────────────────
        if (reWifi.matches(t))       return toggleIntent(t, IntentAction.SET_WIFI, text)
        if (reBluetooth.matches(t))  return toggleIntent(t, IntentAction.SET_BLUETOOTH, text)
        if (reFlashlight.matches(t)) return toggleIntent(t, IntentAction.SET_FLASHLIGHT, text)

        // ── 12. Volume ───────────────────────────────────────────────────
        if (reVolume.matches(t)) {
            val dir = if (t.contains("up") || t.contains("max") ||
                t.contains("raise") || t.contains("increase")) "up" else "down"
            return action(IntentAction.SET_VOLUME, text,
                extra = mapOf(IntentExtra.DIRECTION to dir))
        }

        // ── 13. B1: Search ───────────────────────────────────────────────
        reSearch.find(t)?.let { m ->
            val query = m.groupValues[2].trim()
            if (query.isNotBlank()) {
                return action(IntentAction.SEARCH_QUERY, text, target = query)
            }
        }

        // ── 14. Conversation ─────────────────────────────────────────────
        if (reTime.matches(t))     return conv(IntentAction.TIME, text)
        if (reDate.matches(t))     return conv(IntentAction.DATE, text)
        if (reGreeting.matches(t)) return conv(IntentAction.GREETING, text)
        if (reStop.matches(t))     return conv(IntentAction.STOP, text)

        // ── 15. Cloud fallback ───────────────────────────────────────────
        if (t.length > 12) return cloudIntent(text)

        return unknown(text)
    }

    // ══════════════════════════════════════════════════════════════════════
    // Message intent parsing
    // ══════════════════════════════════════════════════════════════════════

    private fun isMessageIntent(t: String): Boolean {
        return (t.startsWith("message ") || t.startsWith("text ") ||
                t.startsWith("tell ") || t.startsWith("whatsapp ") ||
                t.contains("send sms") || t.contains("send a text") ||
                t.contains("send whatsapp") || t.contains("send message"))
    }

    private fun messageIntent(t: String, raw: String): ZaraIntent {
        val channel = when {
            reWhatsappChannel.matches(t) -> ChannelType.WHATSAPP
            reSmsChannel.matches(t)      -> ChannelType.SMS
            else                         -> ChannelType.SMS
        }
        val action = if (channel == ChannelType.WHATSAPP) IntentAction.SEND_WHATSAPP
                     else IntentAction.SEND_SMS

        reMsgVerb.find(t)?.let { m ->
            val contact = m.groupValues[1].trim()
            val body    = m.groupValues[2].trim()
            if (contact.isNotBlank()) {
                return action(action, raw, target = contact,
                    extra = buildMap {
                        if (body.isNotBlank()) put(IntentExtra.BODY, body)
                        put(IntentExtra.CHANNEL, channel)
                    })
            }
        }

        reMsgFallback.find(t)?.let { m ->
            val contact = m.groupValues[1].trim()
            if (contact.isNotBlank()) {
                return action(action, raw, target = contact,
                    extra = mapOf(IntentExtra.CHANNEL to channel))
            }
        }

        return unknown(raw)
    }

    // ══════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════

    private fun toggleIntent(t: String, intentAction: String, raw: String): ZaraIntent {
        val isOff = reOffKeyword.matches(t)
        val isOn  = !isOff && (reOnKeyword.matches(t) || t.contains("on"))
        return action(intentAction, raw, extra = mapOf(IntentExtra.ON to isOn.toString()))
    }

    private fun action(
        a: String, raw: String,
        target: String? = null,
        extra: Map<String, String> = emptyMap()
    ) = ZaraIntent(IntentType.ACTION, a, target, extra, rawText = raw)

    private fun conv(a: String, raw: String) =
        ZaraIntent(IntentType.CONVERSATION, a, rawText = raw)

    private fun cloudIntent(raw: String) =
        ZaraIntent(IntentType.CLOUD, IntentAction.QUERY, rawText = raw)

    private fun unknown(raw: String) =
        ZaraIntent(IntentType.UNKNOWN, IntentAction.UNKNOWN, rawText = raw)
}
