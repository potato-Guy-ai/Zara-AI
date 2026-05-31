package com.zara.assistant.core

/**
 * Offline intent classifier.
 * All Regex compiled once at class load — not per call.
 * Returns structured ZaraIntent with typed actions and extras.
 */
class LocalIntentClassifier {

    // ── Compiled regex cache ────────────────────────────────────────────────
    private val reCall        = Regex(".*(call|ring|dial|phone)(.*)")
    private val reAnswerCall  = Regex(".*(answer|pick up).*(call).*")
    private val reEndCall     = Regex(".*(hang up|end call|reject call|disconnect).*")
    private val reMessage     = Regex(".*(send|text|message|whatsapp|msg).*")
    private val reOpen        = Regex(".*(open|launch|start|switch to)(.*)")
    private val reWifi        = Regex(".*(wi.?fi).*")
    private val reBluetooth   = Regex(".*(bluetooth|bt).*")
    private val reFlashlight  = Regex(".*(flashlight|torch).*")
    private val reVolume      = Regex(".*(volume|vol).*(up|down|max|low|raise|lower|increase|decrease).*")
    private val reSilent      = Regex(".*(silent mode|silence|do not disturb|dnd).*")
    private val reMute        = Regex("^mute$|^mute everything$")
    private val reLock        = Regex(".*(lock).*(phone|screen|device).*")
    private val reCamera      = Regex(".*(take photo|take picture|open camera|selfie|capture).*")
    private val reAlarm       = Regex(".*(set|create|add).*(alarm).*")
    private val reTimer       = Regex(".*(set|start|create).*(timer).*")
    private val reNavigate    = Regex(".*(navigate|directions|take me|go to|drive to)(.*)")
    private val rePlay        = Regex(".*(play|listen to)(.*)")
    private val reTime        = Regex(".*(what.?s the time|what time|current time|time now|time is it).*")
    private val reDate        = Regex(".*(what.?s the date|what date|today.?s date|what day|day is it).*")
    private val reGreeting    = Regex(".*(how are you|you okay|you good|what.?s up|hey|hello|hi zara).*")
    private val reStop        = Regex(".*(stop listening|go to sleep|goodbye|bye zara|shut up|cancel|never mind).*")
    private val reOffKeyword  = Regex(".*(turn off|switch off|disable|deactivate).*")
    private val reOnKeyword   = Regex(".*(turn on|switch on|enable|activate).*")
    private val reAfterVerb   = Regex("(?:call|ring|dial|phone|open|launch|start|switch to|navigate to|go to|take me to|play|listen to)\\s+(.+)")
    private val reBetween     = Regex("(?:to|for)\\s+(.+?)\\s+(?:saying|that says|with message|with body)")
    private val reBody        = Regex("(?:saying|that says|with message|with body)\\s+(.+)")

    // ── Public API ──────────────────────────────────────────────────────────

    fun classify(text: String): ZaraIntent {
        if (text.isBlank()) return unknown(text)
        val t = text.lowercase().trim()

        return when {
            reAnswerCall.matches(t)  -> intent(IntentType.ACTION, IntentAction.ANSWER_CALL, text)
            reEndCall.matches(t)     -> intent(IntentType.ACTION, IntentAction.END_CALL, text)
            reCall.matches(t)        -> callIntent(t, text)

            reMessage.matches(t)     -> messageIntent(t, text)

            reNavigate.matches(t)    -> {
                val target = extractAfterVerb(t) ?: return unknown(text)
                intent(IntentType.ACTION, IntentAction.NAVIGATE_TO, text, target = target)
            }

            rePlay.matches(t) -> {
                val target = extractAfterVerb(t)
                intent(IntentType.ACTION, IntentAction.PLAY_MUSIC, text, target = target)
            }

            reOpen.matches(t) -> {
                val target = extractAfterVerb(t) ?: return unknown(text)
                intent(IntentType.ACTION, IntentAction.OPEN_APP, text, target = target)
            }

            reCamera.matches(t)  -> intent(IntentType.ACTION, IntentAction.OPEN_CAMERA, text)
            reAlarm.matches(t)   -> intent(IntentType.ACTION, IntentAction.SET_ALARM, text)
            reTimer.matches(t)   -> intent(IntentType.ACTION, IntentAction.SET_TIMER, text)
            reLock.matches(t)    -> intent(IntentType.ACTION, IntentAction.LOCK_SCREEN, text)

            reWifi.matches(t)       -> toggleIntent(t, IntentAction.SET_WIFI, text)
            reBluetooth.matches(t)  -> toggleIntent(t, IntentAction.SET_BLUETOOTH, text)
            reFlashlight.matches(t) -> toggleIntent(t, IntentAction.SET_FLASHLIGHT, text)

            reVolume.matches(t) -> {
                val dir = if (t.contains("up") || t.contains("max") ||
                              t.contains("raise") || t.contains("increase")) "up" else "down"
                intent(IntentType.ACTION, IntentAction.SET_VOLUME, text,
                    extra = mapOf(IntentExtra.DIRECTION to dir))
            }

            // Silent mode: explicit phrases only — does not capture plain "mute" from volume context
            reSilent.matches(t) || reMute.matches(t) ->
                intent(IntentType.ACTION, IntentAction.SET_SILENT, text,
                    extra = mapOf(IntentExtra.ON to "true"))

            reTime.matches(t)     -> intent(IntentType.CONVERSATION, IntentAction.TIME, text)
            reDate.matches(t)     -> intent(IntentType.CONVERSATION, IntentAction.DATE, text)
            reGreeting.matches(t) -> intent(IntentType.CONVERSATION, IntentAction.GREETING, text)
            reStop.matches(t)     -> intent(IntentType.CONVERSATION, IntentAction.STOP, text)

            // Cloud fallback: only for genuine queries, not short noise
            t.length > 15 -> intent(IntentType.CLOUD, IntentAction.QUERY, text)

            else -> unknown(text)
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun callIntent(t: String, raw: String): ZaraIntent {
        val target = extractAfterVerb(t)
        return if (target.isNullOrBlank()) unknown(raw)
        else intent(IntentType.ACTION, IntentAction.CALL, raw, target = target)
    }

    private fun messageIntent(t: String, raw: String): ZaraIntent {
        val target = reBetween.find(t)?.groupValues?.getOrNull(1)?.trim()
        val body   = reBody.find(t)?.groupValues?.getOrNull(1)?.trim()
        return intent(
            IntentType.ACTION, IntentAction.SEND_SMS, raw,
            target = target,
            extra = buildMap {
                if (body != null) put(IntentExtra.BODY, body)
            }
        )
    }

    /**
     * Toggle intent: explicit "off" keywords take priority over "on".
     * Fixes: "turn wifi off" → was incorrectly returning on=true.
     */
    private fun toggleIntent(t: String, action: String, raw: String): ZaraIntent {
        val isOff = reOffKeyword.matches(t)
        val isOn  = !isOff && (reOnKeyword.matches(t) || t.contains("on"))
        return intent(IntentType.ACTION, action, raw,
            extra = mapOf(IntentExtra.ON to isOn.toString()))
    }

    private fun extractAfterVerb(t: String): String? =
        reAfterVerb.find(t)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }

    private fun intent(
        type: IntentType,
        action: String,
        raw: String,
        target: String? = null,
        extra: Map<String, String> = emptyMap()
    ) = ZaraIntent(type, action, target, extra, rawText = raw)

    private fun unknown(raw: String) =
        ZaraIntent(IntentType.UNKNOWN, IntentAction.UNKNOWN, rawText = raw)
}
