package com.zara.assistant.core

import java.text.Normalizer

/**
 * Layer 4 — Intent Atomization + Safety Gateway Engine.
 *
 * Responsibilities:
 *   1. Input safety: normalize, reject garbage
 *   2. Atomize compound input into independent intent segments
 *   3. Extract and type-enforce slots (one SlotType per token)
 *   4. Validate required slots per intent type
 *   5. Score confidence per slot and per intent
 *   6. Return a strict IntentGraph output contract
 *
 * Rules:
 *   - No entity resolution
 *   - No app resolution
 *   - No execution logic
 *   - No NLP models
 *   - O(n) deterministic processing
 *   - Immutable output
 */
object IntentAtomizer {

    private const val MAX_INPUT_LENGTH  = 500
    private const val MIN_INPUT_LENGTH  = 2
    private const val HIGH_CONFIDENCE   = 0.9f
    private const val MEDIUM_CONFIDENCE = 0.65f
    private const val LOW_CONFIDENCE    = 0.3f

    // Split delimiters (mirrors CompoundIntentSplitter but operates earlier, pre-pipeline)
    private val SPLIT_DELIMITERS = listOf(" after that ", " then ", " and ", " & ", "&")

    // ── Action keyword maps ───────────────────────────────────────────────────

    private val CALL_TRIGGERS    = setOf("call", "phone", "dial", "ring")
    private val MESSAGE_TRIGGERS = setOf("message", "msg", "text", "send", "whatsapp", "sms", "telegram", "signal")
    private val APP_TRIGGERS     = setOf("open", "launch", "start", "run")
    private val MEDIA_TRIGGERS   = setOf("play", "listen", "stream", "put on")
    private val NAV_TRIGGERS     = setOf("navigate", "directions", "route", "go to", "take me to")
    private val SEARCH_TRIGGERS  = setOf("search", "look up", "find", "google", "youtube")
    private val TIMER_TRIGGERS   = setOf("timer", "alarm", "remind", "set", "countdown")
    private val SYSTEM_TRIGGERS  = setOf("wifi", "bluetooth", "flashlight", "torch", "volume",
                                         "silent", "ringer", "lock", "brightness")
    private val GREETING_TRIGGERS = setOf("hello", "hi", "hey", "good morning", "good evening",
                                          "what's up", "whats up")

    // Prepositions to strip before contact/app extraction
    private val STRIP_PREPOSITIONS = setOf("to", "for", "from", "at", "on", "in", "with",
                                           "saying", "and say", "tell")

    // ── Public API ───────────────────────────────────────────────────────────

    fun atomize(rawInput: String?): IntentGraph {
        // ── 1. Input Safety ───────────────────────────────────────────────
        if (rawInput == null)       return IntentGraph.rejected("Input is null")
        val normalized = normalize(rawInput)
        if (normalized.length < MIN_INPUT_LENGTH) return IntentGraph.rejected("Input too short")
        if (normalized.length > MAX_INPUT_LENGTH) return IntentGraph.rejected("Input too long (>${MAX_INPUT_LENGTH} chars)")
        if (isPureNoise(normalized))              return IntentGraph.rejected("Input is noise or unparseable")

        // ── 2. Atomize into segments ──────────────────────────────────────────
        val segments = splitSegments(normalized)

        // ── 3+4+5. Parse each segment into AtomicIntent ────────────────────
        val intents = segments.map { parseSegment(it) }

        // ── 5. Validation Gate ─────────────────────────────────────────────
        val needsClarification = intents.filter { it.confidence < 0.5f }
        if (needsClarification.isNotEmpty() && intents.all { it.confidence < 0.5f }) {
            return IntentGraph.needsClarification(intents)
        }

        // Validate required slots
        for (intent in intents) {
            val validationError = validateSlots(intent)
            if (validationError != null) {
                // Single-intent hard fail; multi-intent soft fail
                return if (intents.size == 1) IntentGraph.rejected(validationError)
                       else IntentGraph.needsClarification(intents)
            }
        }

        return IntentGraph.valid(intents)
    }

    // ── Normalization ─────────────────────────────────────────────────────────

    private fun normalize(input: String): String {
        // Unicode normalization (NFC)
        var s = Normalizer.normalize(input, Normalizer.Form.NFC)
        // Collapse whitespace
        s = s.replace("\t", " ").replace("\n", " ").replace("\r", " ")
        s = s.split(" ").filter { it.isNotBlank() }.joinToString(" ")
        // Strip leading punctuation noise
        s = s.trimStart('.', ',', '!', '?', '-', '_')
        return s.trim().lowercase()
    }

    private fun isPureNoise(s: String): Boolean {
        val alphanum = s.count { it.isLetterOrDigit() }
        return alphanum < 2
    }

    // ── Segment splitting ─────────────────────────────────────────────────────

    private fun splitSegments(text: String): List<String> {
        val result = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            var bestIdx = -1
            var bestDelim = ""
            for (delim in SPLIT_DELIMITERS) {
                val idx = remaining.indexOf(delim)
                if (idx >= 0 && (bestIdx < 0 || idx < bestIdx)) { bestIdx = idx; bestDelim = delim }
            }
            if (bestIdx < 0) { result.add(remaining.trim()); break }
            val before = remaining.substring(0, bestIdx).trim()
            if (before.isNotEmpty()) result.add(before)
            remaining = remaining.substring(bestIdx + bestDelim.length)
        }
        return if (result.isEmpty()) listOf(text) else result
    }

    // ── Segment parsing ──────────────────────────────────────────────────────

    private fun parseSegment(segment: String): AtomicIntent {
        val words = segment.split(" ")
        val intentType = classifyType(segment, words)
        val slots = extractSlots(intentType, segment, words)
        val confidence = computeConfidence(intentType, slots, segment)
        return AtomicIntent(
            type        = intentType,
            slots       = slots,
            confidence  = confidence,
            rawSegment  = segment
        )
    }

    // ── Intent type classification ─────────────────────────────────────────────

    private fun classifyType(segment: String, words: List<String>): AtomicIntentType {
        if (GREETING_TRIGGERS.any { segment.startsWith(it) }) return AtomicIntentType.GREETING
        val first = words.firstOrNull() ?: return AtomicIntentType.UNKNOWN
        return when {
            CALL_TRIGGERS.contains(first)                              -> AtomicIntentType.CALL
            MESSAGE_TRIGGERS.any { segment.contains(it) }             -> AtomicIntentType.MESSAGE
            NAV_TRIGGERS.any { segment.contains(it) }                 -> AtomicIntentType.NAVIGATE
            SEARCH_TRIGGERS.any { segment.startsWith(it) }            -> AtomicIntentType.SEARCH
            MEDIA_TRIGGERS.contains(first)                             -> AtomicIntentType.PLAY_MEDIA
            APP_TRIGGERS.contains(first)                              -> AtomicIntentType.OPEN_APP
            TIMER_TRIGGERS.any { segment.contains(it) }
                && (segment.contains("minute") || segment.contains("second") ||
                    segment.contains("hour") || segment.contains("timer") ||
                    segment.contains("alarm"))                         -> AtomicIntentType.SET_TIMER
            SYSTEM_TRIGGERS.any { segment.contains(it) }              -> AtomicIntentType.SYSTEM_CONTROL
            else                                                       -> AtomicIntentType.UNKNOWN
        }
    }

    // ── Slot extraction (one SlotType per token) ───────────────────────────

    private fun extractSlots(type: AtomicIntentType, segment: String, words: List<String>): List<TypedSlot> {
        val slots = mutableListOf<TypedSlot>()
        when (type) {
            AtomicIntentType.CALL -> {
                // "call <contact>"
                val trigger = words.firstOrNull { CALL_TRIGGERS.contains(it) }
                if (trigger != null) slots.add(TypedSlot(SlotType.ACTION_SLOT, trigger, HIGH_CONFIDENCE))
                val afterTrigger = extractAfterKeyword(segment, CALL_TRIGGERS)
                if (afterTrigger != null) {
                    slots.add(TypedSlot(SlotType.CONTACT_SLOT, afterTrigger, HIGH_CONFIDENCE))
                    slots.add(TypedSlot(SlotType.CALL_TARGET_SLOT, afterTrigger, HIGH_CONFIDENCE))
                }
            }
            AtomicIntentType.MESSAGE -> {
                val trigger = MESSAGE_TRIGGERS.firstOrNull { segment.contains(it) }
                if (trigger != null) slots.add(TypedSlot(SlotType.ACTION_SLOT, trigger, HIGH_CONFIDENCE))
                // Contact: after trigger, before "saying"/"that"/message body
                val contact = extractContactFromMessage(segment)
                if (contact != null) slots.add(TypedSlot(SlotType.CONTACT_SLOT, contact, HIGH_CONFIDENCE))
                // Message body: after "saying" / " with message " / " that "
                val body = extractMessageBody(segment)
                if (body != null) slots.add(TypedSlot(SlotType.MESSAGE_SLOT, body, HIGH_CONFIDENCE))
            }
            AtomicIntentType.OPEN_APP -> {
                val trigger = words.firstOrNull { APP_TRIGGERS.contains(it) }
                if (trigger != null) slots.add(TypedSlot(SlotType.ACTION_SLOT, trigger, HIGH_CONFIDENCE))
                val app = extractAfterKeyword(segment, APP_TRIGGERS)
                if (app != null) slots.add(TypedSlot(SlotType.APP_SLOT, app, HIGH_CONFIDENCE))
            }
            AtomicIntentType.PLAY_MEDIA -> {
                val trigger = words.firstOrNull { MEDIA_TRIGGERS.contains(it) }
                if (trigger != null) slots.add(TypedSlot(SlotType.ACTION_SLOT, trigger, HIGH_CONFIDENCE))
                val content = extractAfterKeyword(segment, MEDIA_TRIGGERS)
                if (content != null) slots.add(TypedSlot(SlotType.CONTENT_SLOT, content, MEDIUM_CONFIDENCE))
            }
            AtomicIntentType.NAVIGATE -> {
                slots.add(TypedSlot(SlotType.ACTION_SLOT, "navigate", HIGH_CONFIDENCE))
                val dest = extractNavigationTarget(segment)
                if (dest != null) slots.add(TypedSlot(SlotType.QUERY_SLOT, dest, HIGH_CONFIDENCE))
            }
            AtomicIntentType.SEARCH -> {
                val trigger = SEARCH_TRIGGERS.firstOrNull { segment.startsWith(it) }
                if (trigger != null) slots.add(TypedSlot(SlotType.ACTION_SLOT, trigger, HIGH_CONFIDENCE))
                val query = extractAfterKeyword(segment, SEARCH_TRIGGERS)
                    ?.removePrefix("for ")?.removePrefix("about ")?.trim()
                if (query != null && query.isNotBlank()) slots.add(TypedSlot(SlotType.QUERY_SLOT, query, HIGH_CONFIDENCE))
            }
            AtomicIntentType.SET_TIMER -> {
                slots.add(TypedSlot(SlotType.ACTION_SLOT, "set_timer", HIGH_CONFIDENCE))
                // Duration extracted as QUERY_SLOT (numeric string); normalization done in Layer 4B
                val duration = extractDurationString(segment)
                if (duration != null) slots.add(TypedSlot(SlotType.QUERY_SLOT, duration, HIGH_CONFIDENCE))
            }
            AtomicIntentType.SYSTEM_CONTROL -> {
                val keyword = SYSTEM_TRIGGERS.firstOrNull { segment.contains(it) }
                if (keyword != null) slots.add(TypedSlot(SlotType.ACTION_SLOT, keyword, HIGH_CONFIDENCE))
            }
            AtomicIntentType.GREETING -> {
                slots.add(TypedSlot(SlotType.ACTION_SLOT, "greeting", HIGH_CONFIDENCE))
            }
            AtomicIntentType.UNKNOWN -> {
                // Best-effort: store entire segment as QUERY_SLOT
                slots.add(TypedSlot(SlotType.QUERY_SLOT, segment, LOW_CONFIDENCE))
            }
        }
        return slots
    }

    // ── Slot extraction helpers ────────────────────────────────────────────────

    private fun extractAfterKeyword(segment: String, keywords: Set<String>): String? {
        for (kw in keywords.sortedByDescending { it.length }) {
            val idx = segment.indexOf(kw)
            if (idx >= 0) {
                val after = segment.substring(idx + kw.length).trim()
                val stripped = stripLeadingPrepositions(after)
                if (stripped.isNotBlank()) return stripped
            }
        }
        return null
    }

    private fun stripLeadingPrepositions(text: String): String {
        var s = text
        for (prep in STRIP_PREPOSITIONS.sortedByDescending { it.length }) {
            if (s.startsWith("$prep ")) { s = s.removePrefix("$prep ").trim(); break }
        }
        return s
    }

    private fun extractContactFromMessage(segment: String): String? {
        // Patterns: "message <contact>", "send <contact>", "whatsapp <contact>"
        val triggerEnd = MESSAGE_TRIGGERS
            .mapNotNull { kw -> segment.indexOf(kw).takeIf { it >= 0 }?.let { it + kw.length } }
            .minOrNull() ?: return null
        val afterTrigger = segment.substring(triggerEnd).trim()
        val stripped = stripLeadingPrepositions(afterTrigger)
        // Stop at "saying", "that", "with message"
        val stoppers = listOf(" saying ", " that ", " with message ", " with ")
        for (stopper in stoppers) {
            val si = stripped.indexOf(stopper)
            if (si > 0) return stripped.substring(0, si).trim()
        }
        // If no stopper, entire remainder is contact (no body detected)
        return if (stripped.isNotBlank()) stripped else null
    }

    private fun extractMessageBody(segment: String): String? {
        val markers = listOf(" saying ", " that ", " with message ")
        for (marker in markers) {
            val idx = segment.indexOf(marker)
            if (idx >= 0) {
                val body = segment.substring(idx + marker.length).trim()
                if (body.isNotBlank()) return body
            }
        }
        return null
    }

    private fun extractNavigationTarget(segment: String): String? {
        val markers = listOf("navigate to ", "directions to ", "route to ", "go to ", "take me to ")
        for (marker in markers) {
            val idx = segment.indexOf(marker)
            if (idx >= 0) {
                val dest = segment.substring(idx + marker.length).trim()
                    .let { s -> val onIdx = s.lastIndexOf(" on "); if (onIdx > 0) s.substring(0, onIdx).trim() else s }
                if (dest.isNotBlank()) return dest
            }
        }
        return null
    }

    private fun extractDurationString(segment: String): String? {
        val units = listOf("hour", "hr", "minute", "min", "second", "sec")
        val words = segment.split(" ")
        val parts = mutableListOf<String>()
        var i = 0
        while (i < words.size) {
            val w = words[i]
            if (w.all { it.isDigit() } && i + 1 < words.size) {
                val unit = words[i + 1].trimEnd('s')
                if (units.any { unit.startsWith(it) }) { parts.add("${w} ${words[i + 1]}"); i += 2; continue }
            }
            i++
        }
        return if (parts.isNotEmpty()) parts.joinToString(" ") else null
    }

    // ── Slot validation ──────────────────────────────────────────────────────────

    /** Returns an error string if required slots are missing, else null. */
    private fun validateSlots(intent: AtomicIntent): String? = when (intent.type) {
        AtomicIntentType.CALL    -> if (intent.slot(SlotType.CONTACT_SLOT) == null)
                                        "CALL intent missing CONTACT_SLOT" else null
        AtomicIntentType.MESSAGE -> when {
            intent.slot(SlotType.CONTACT_SLOT) == null -> "MESSAGE intent missing CONTACT_SLOT"
            else                                       -> null   // body optional (open chat)
        }
        AtomicIntentType.OPEN_APP -> if (intent.slot(SlotType.APP_SLOT) == null)
                                         "OPEN_APP intent missing APP_SLOT" else null
        AtomicIntentType.SEARCH   -> if (intent.slot(SlotType.QUERY_SLOT) == null)
                                         "SEARCH intent missing QUERY_SLOT" else null
        else -> null
    }

    // ── Confidence scoring ─────────────────────────────────────────────────────

    private fun computeConfidence(type: AtomicIntentType, slots: List<TypedSlot>, segment: String): Float {
        if (type == AtomicIntentType.UNKNOWN) return LOW_CONFIDENCE
        if (slots.isEmpty()) return LOW_CONFIDENCE
        // Average slot confidence, weighted down if type is ambiguous
        val avg = slots.map { it.confidence }.average().toFloat()
        return when {
            type == AtomicIntentType.UNKNOWN -> LOW_CONFIDENCE
            slots.any { it.confidence < 0.5f } -> MEDIUM_CONFIDENCE.coerceAtMost(avg)
            else -> avg
        }
    }
}
