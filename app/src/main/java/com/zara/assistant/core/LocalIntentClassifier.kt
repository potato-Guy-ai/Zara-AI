package com.zara.assistant.core

import com.zara.assistant.core.messaging.MessageNLU
import com.zara.assistant.core.semantic.MiniLMManager
import com.zara.assistant.core.semantic.SemanticIntentEngine
import com.zara.assistant.core.semantic.SemanticIntentMapper
import com.zara.assistant.utils.ZaraLogger

/**
 * B2.2 + Layer 6.5F Phase 1
 *
 * Added MEDIA_CONTROL classification:
 *   pause / resume / next / previous / stop music / next song etc.
 *   Resolved via MediaControlAction.fromText() — no regex duplication.
 *   Stored in MEDIA_ACTION extra. Inserted before search step.
 *
 * Layer 6.7 Phase 3 — Messaging NLU fallback:
 *   When the old command-style message parser (isMessageIntent/messageIntent)
 *   either doesn't recognize the utterance at all, or recognizes the gate but
 *   fails to extract a usable intent (e.g. "tell amma I'll be late" — passes
 *   the "tell " prefix gate but has no "saying/that/:" delimiter), this now
 *   falls back to MessageNLU.parse() for casual phrasings: "tell X body",
 *   "let X know body", "ask X if/whether body", "say body to X",
 *   "inform X body". MessageNLU is parser-only (returns MessageParseResult);
 *   ZaraIntent construction stays centralized here via action(), unchanged
 *   from how the old parser already built SEND_WHATSAPP/SEND_SMS intents.
 *   Inserted at the same priority position the old message check already
 *   occupied — still after reKnowledge/reCallAction, still before
 *   reNavigate/MediaControlAction/rePlay/reOpenTrigger/reSearch — so
 *   existing command-style behavior and "tell me about X" knowledge
 *   questions are unaffected.
 *
 * Layer 6.6 — MiniLM semantic fallback:
 *   Injected BEFORE the final cloud/unknown fallback. When every rule and
 *   the MessageNLU parser have failed to produce a non-UNKNOWN intent,
 *   SemanticIntentEngine.resolve() is called once. The model is lazy-loaded
 *   on first use (MiniLMManager.loadModel() is a no-op if already loaded).
 *   If SemanticIntentMapper returns a valid ZaraIntent, it is returned
 *   immediately and logged. If it returns null (low confidence / UNKNOWN /
 *   fallbackRequired), we fall through to the existing cloud/unknown path
 *   unchanged.
 *
 *   Dependency wiring: SemanticIntentEngine is injected via constructor
 *   (preferred per spec). A zero-argument convenience constructor creates
 *   the default stack (MiniLMManager → SemanticIntentEngine) so all
 *   existing call sites that do `LocalIntentClassifier()` need no changes.
 *
 * Layer 6.6 bugfix — reCallAction false-positive on reminder phrases:
 *   reCallAction uses find() (substring match), so "remind me to call X"
 *   was matching "call X" mid-string and returning CALL before MiniLM ran.
 *   Fix: reReminderPrefix guard added before the reCallAction block.
 *   Any text starting with a reminder trigger word bypasses the CALL branch
 *   entirely and falls through to MiniLM. Direct call commands are unaffected.
 */
class LocalIntentClassifier(
    private val semanticIntentEngine: SemanticIntentEngine = SemanticIntentEngine(MiniLMManager())
) {

    private val reKnowledge = Regex(
        ".*(how (do|does|can|would|to)|what is|what are|explain|tell me about|" +
        "describe|difference between|meaning of|definition of|understand).*"
    )
    private val reCallAction  = Regex("(?:call|dial|phone|ring|make (?:a )?call (?:to|for))\\s+(.+)")
    // Layer 6.6 bugfix: reminder phrases must never trigger the CALL rule.
    // reCallAction.find() is a substring match — it fires on "remind me to call X"
    // by finding "call X" mid-string. Guard against this with a prefix check.
    private val reReminderPrefix = Regex("^(?:remind\\b|set (?:a )?reminder\\b|reminder to\\b).*")
    private val reAnswerCall  = Regex(".*(answer|pick up).*(call).*")
    private val reEndCall     = Regex(".*(hang up|end call|end the call|reject call|disconnect).*")
    private val reWhatsappChannel = Regex(".*whatsapp.*")
    private val reSmsChannel      = Regex(".*(send sms|send a text|text message|sms to).*")
    private val reMsgVerb    = Regex(
        "(?:message|text|tell|whatsapp|msg|send(?: (?:sms|a text|whatsapp(?: message)?))?(?: to)?)\\s+([\\w\\s]+?)\\s+(?:saying|that|with message|with body|that says|:)\\s*(.*)",
        RegexOption.IGNORE_CASE
    )
    private val reMsgFallback = Regex("(?:message|text|whatsapp|msg)\\s+(.+)")
    private val reOpenVerb    = Regex("(?:open|launch|start|switch to|go to app)\\s+(.+)")
    private val reOpenTrigger = Regex(".*(open|launch|start|switch to|go to app).*")
    private val reSearch      = Regex("(?:search(?: for)?|find|look(?:ing)? (?:up|for))\\s+(.+)")
    private val reWifi        = Regex(".*(wi.?fi).*")
    private val reBluetooth   = Regex(".*(bluetooth|bt).*")
    private val reFlashlight  = Regex(".*(flashlight|torch).*")
    private val reVolume      = Regex(".*(volume|vol).*(up|down|max|low|raise|lower|increase|decrease).*")
    private val reLock        = Regex(".*(lock).*(phone|screen|device).*")
    private val reCamera      = Regex(".*(take photo|take picture|open camera|selfie|capture).*")
    private val reAlarmWithTime = Regex(".*(set|create|add).*(alarm).*(\\d|am|pm).*")
    private val reAlarmBare     = Regex(".*(set|create|add).*(alarm).*")
    private val reTimer         = Regex(".*(set|start|create).*(timer).*")
    private val reShowAlarms  = Regex(".*(show|list|view|see).*(alarms?).*")
    private val reShowTimers  = Regex(".*(show|list|view|see).*(timers?).*")
    private val reOpenClock   = Regex(".*(open|launch).*(clock).*")
    private val reNavigate    = Regex(".*(navigate to|directions to|take me to|drive to)\\s+(.+)")
    private val rePlay        = Regex("(?:play|listen to)\\s+(.+)")
    private val reSilentOn    = Regex(".*(turn on silent|enable silent|silent mode|put on silent|do not disturb|dnd|^mute$|^mute everything$).*")
    private val reSilentOff   = Regex(".*(turn off silent|disable silent|normal mode|ring mode|ringer on|unmute).*")
    private val reVibrate     = Regex(".*(vibrate mode|enable vibrate|turn on vibrate|set to vibrate).*")
    private val reOffKeyword  = Regex(".*(turn off|switch off|disable|deactivate).*")
    private val reOnKeyword   = Regex(".*(turn on|switch on|enable|activate).*")
    private val reTime        = Regex(".*(what.?s the time|what time|current time|time now|time is it).*")
    private val reDate        = Regex(".*(what.?s the date|what date|today.?s date|what day|day is it).*")
    private val reGreeting    = Regex(".*(how are you|you okay|you good|hey zara|hello zara|hi zara).*")
    private val reStop        = Regex(".*(stop listening|go to sleep|goodbye|bye zara|shut up|cancel|never mind).*")

    fun classify(text: String): ZaraIntent {
        if (text.isBlank()) return unknown(text)
        val t = text.lowercase().trim()

        if (reKnowledge.matches(t)) return cloudIntent(text)

        if (reAnswerCall.matches(t)) return action(IntentAction.ANSWER_CALL, text)
        if (reEndCall.matches(t))    return action(IntentAction.END_CALL, text)
        // Layer 6.6 bugfix: skip CALL branch for reminder-prefixed inputs.
        // "remind me to call X" must reach MiniLM, not be claimed here.
        if (!reReminderPrefix.matches(t)) {
            reCallAction.find(t)?.let { m ->
                val target = m.groupValues[1].trim()
                if (target.isNotBlank()) return action(IntentAction.CALL, text, target = target)
            }
        }

        if (isMessageIntent(t)) {
            val intent = messageIntent(t, text)
            if (intent.action != IntentAction.UNKNOWN) return intent
            // Old command-style parser recognized the gate (e.g. "tell ")
            // but couldn't extract contact/body (no "saying"/"that"/":"  etc.)
            // — fall through to MessageNLU below instead of returning UNKNOWN.
        }
        // Layer 6.7 Phase 3: casual-phrasing fallback (tell/let/ask/say/inform).
        // Independent of isMessageIntent() since most of these patterns don't
        // share its trigger words (e.g. "let dad know..." / "ask mom if...").
        MessageNLU.parse(t)?.let { result ->
            val intentAction = if (result.channel == ChannelType.WHATSAPP) IntentAction.SEND_WHATSAPP else IntentAction.SEND_SMS
            return action(
                intentAction, text, target = result.contact,
                extra = buildMap {
                    if (!result.body.isNullOrBlank()) put(IntentExtra.BODY, result.body)
                    put(IntentExtra.CHANNEL, result.channel)
                    put("message_confidence", result.confidence.name)
                }
            )
        }

        reNavigate.find(t)?.let { m ->
            val dest = m.groupValues[2].trim()
            if (dest.isNotBlank()) return action(IntentAction.NAVIGATE_TO, text, target = dest)
        }

        // Layer 6.5F Phase 1: media control before play/open
        com.zara.assistant.media.MediaControlAction.fromText(t)?.let { mediaAction ->
            return action(IntentAction.MEDIA_CONTROL, text,
                extra = mapOf(IntentExtra.MEDIA_ACTION to mediaAction.name))
        }

        rePlay.find(t)?.let { m ->
            val query = m.groupValues[1].trim()
            if (query.isNotBlank()) return action(IntentAction.PLAY_MUSIC, text, target = query)
        }

        if (reOpenTrigger.matches(t)) {
            if (reOpenClock.matches(t)) return action(IntentAction.OPEN_CLOCK, text)
            reOpenVerb.find(t)?.let { m ->
                val appName = m.groupValues[1].trim()
                if (appName.isNotBlank()) return action(IntentAction.OPEN_APP, text, target = appName)
            }
        }

        if (reCamera.matches(t)) return action(IntentAction.OPEN_CAMERA, text)

        if (reShowAlarms.matches(t))    return action(IntentAction.SHOW_ALARMS, text)
        if (reShowTimers.matches(t))    return action(IntentAction.SHOW_TIMERS, text)
        if (reAlarmWithTime.matches(t)) return action(IntentAction.SET_ALARM, text)
        if (reAlarmBare.matches(t))     return action(IntentAction.SHOW_ALARMS, text)
        if (reTimer.matches(t))         return action(IntentAction.SET_TIMER, text)

        if (reLock.matches(t)) return action(IntentAction.LOCK_SCREEN, text)

        if (reSilentOff.matches(t)) return action(IntentAction.SET_SILENT, text, extra = mapOf(IntentExtra.ON to "false", IntentExtra.MODE to "normal"))
        if (reVibrate.matches(t))   return action(IntentAction.SET_SILENT, text, extra = mapOf(IntentExtra.ON to "true",  IntentExtra.MODE to "vibrate"))
        if (reSilentOn.matches(t))  return action(IntentAction.SET_SILENT, text, extra = mapOf(IntentExtra.ON to "true",  IntentExtra.MODE to "silent"))

        if (reWifi.matches(t))       return toggleIntent(t, IntentAction.SET_WIFI, text)
        if (reBluetooth.matches(t))  return toggleIntent(t, IntentAction.SET_BLUETOOTH, text)
        if (reFlashlight.matches(t)) return toggleIntent(t, IntentAction.SET_FLASHLIGHT, text)

        if (reVolume.matches(t)) {
            val dir = if (t.contains("up") || t.contains("max") || t.contains("raise") || t.contains("increase")) "up" else "down"
            return action(IntentAction.SET_VOLUME, text, extra = mapOf(IntentExtra.DIRECTION to dir))
        }

        reSearch.find(t)?.let { m ->
            val query = m.groupValues[1].trim()
            if (query.isNotBlank()) return action(IntentAction.SEARCH_QUERY, text, target = query)
        }

        if (reTime.matches(t))     return conv(IntentAction.TIME, text)
        if (reDate.matches(t))     return conv(IntentAction.DATE, text)
        if (reGreeting.matches(t)) return conv(IntentAction.GREETING, text)
        if (reStop.matches(t))     return conv(IntentAction.STOP, text)

        // ── Layer 6.6: MiniLM semantic fallback ───────────────────────────────
        // Every rule and the MessageNLU parser has already failed to claim this
        // input. Try on-device semantic classification before escalating to cloud.
        // Model is lazy-loaded on first call; no cost on the fast (rule-matched) path.
        ZaraLogger.d("[Layer6.6-DIAG] resolve() START text='$text'")
        val semanticResult = semanticIntentEngine.resolve(text)
        ZaraLogger.d("[Layer6.6-DIAG] resolve() DONE intent=${semanticResult.intent} confidence=${semanticResult.confidence} entities=${semanticResult.entities} fallback=${semanticResult.fallbackRequired}")
        ZaraLogger.d("[Layer6.6-DIAG] map() CALL")
        val mappedResult = try {
            SemanticIntentMapper.map(semanticResult, text)
        } catch (e: Exception) {
            ZaraLogger.e("[Layer6.6-DIAG] map() EXCEPTION ${e::class.simpleName}: ${e.message}\n${e.stackTraceToString()}")
            throw e
        }
        ZaraLogger.d("[Layer6.6-DIAG] map() RETURNED ${if (mappedResult == null) "null" else "non-null action=${mappedResult.action} type=${mappedResult.type}"}")
        mappedResult?.let { mapped ->
            ZaraLogger.d("[Layer6.6-DIAG] let{} ENTERED mapped.action=${mapped.action}")
            ZaraLogger.d("[Layer6.6] MiniLM handled intent: ${semanticResult.intent}")
            return mapped
        }
        // ── End Layer 6.6 ─────────────────────────────────────────────────────

        ZaraLogger.d("[Layer6.6-DIAG] FELL THROUGH t.length=${t.length}")
        if (t.length > 12) {
            ZaraLogger.d("[Layer6.6-DIAG] → cloudIntent()")
            return cloudIntent(text)
        }
        ZaraLogger.d("[Layer6.6-DIAG] → unknown()")
        return unknown(text)
    }

    private fun isMessageIntent(t: String): Boolean =
        t.startsWith("message ") || t.startsWith("text ") || t.startsWith("tell ") ||
        t.startsWith("whatsapp ") || t.contains("send sms") || t.contains("send a text") ||
        t.contains("send whatsapp") || t.contains("send message")

    private fun messageIntent(t: String, raw: String): ZaraIntent {
        val channel = when {
            reWhatsappChannel.matches(t) -> ChannelType.WHATSAPP
            reSmsChannel.matches(t)      -> ChannelType.SMS
            else                         -> ChannelType.SMS
        }
        val intentAction = if (channel == ChannelType.WHATSAPP) IntentAction.SEND_WHATSAPP else IntentAction.SEND_SMS
        reMsgVerb.find(t)?.let { m ->
            val contact = m.groupValues[1].trim()
            val body    = m.groupValues[2].trim()
            if (contact.isNotBlank()) return action(intentAction, raw, target = contact, extra = buildMap {
                if (body.isNotBlank()) put(IntentExtra.BODY, body)
                put(IntentExtra.CHANNEL, channel)
            })
        }
        reMsgFallback.find(t)?.let { m ->
            val contact = m.groupValues[1].trim()
            if (contact.isNotBlank()) return action(intentAction, raw, target = contact, extra = mapOf(IntentExtra.CHANNEL to channel))
        }
        return unknown(raw)
    }

    private fun toggleIntent(t: String, intentAction: String, raw: String): ZaraIntent {
        val isOff = reOffKeyword.matches(t)
        val isOn  = !isOff && (reOnKeyword.matches(t) || t.contains("on"))
        return action(intentAction, raw, extra = mapOf(IntentExtra.ON to isOn.toString()))
    }

    private fun action(a: String, raw: String, target: String? = null, extra: Map<String, String> = emptyMap()) =
        ZaraIntent(IntentType.ACTION, a, target, extra, rawText = raw)

    private fun conv(a: String, raw: String) = ZaraIntent(IntentType.CONVERSATION, a, rawText = raw)
    private fun cloudIntent(raw: String)      = ZaraIntent(IntentType.CLOUD, IntentAction.QUERY, rawText = raw)
    private fun unknown(raw: String)          = ZaraIntent(IntentType.UNKNOWN, IntentAction.UNKNOWN, rawText = raw)
}
