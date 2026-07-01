package com.zara.assistant.core.semantic

/**
 * Layer 6.6 — EntityExtractor.
 *
 * Extracts structured entities from raw text, keyed by [SemanticIntent].
 * Intentionally lightweight: regex + string parsing only, no ML,
 * no heavy dependencies.
 *
 * Separation of concerns with MiniLMManager is strict:
 *   MiniLMManager classifies WHAT the user wants (intent category).
 *   EntityExtractor extracts WHO/WHAT/WHEN they're referring to (slots).
 *
 * This mirrors how production NLU systems work (intent model + slot model
 * are separate inference passes), and allows each to evolve independently:
 * entity extraction can remain deterministic even when intent classification
 * moves to ONNX.
 *
 * Supported entity sets per intent:
 *   MESSAGING  → contact, message
 *   MUSIC      → song, app
 *   REMINDER   → duration
 *   (others)   → empty map; add entries as those intents become active
 */
object EntityExtractor {

    fun extract(text: String, intent: SemanticIntent): Map<String, String> {
        val t = text.trim()
        return when (intent) {
            SemanticIntent.MESSAGING  -> extractMessaging(t)
            SemanticIntent.MUSIC      -> extractMusic(t)
            SemanticIntent.REMINDER   -> extractReminder(t)
            else                      -> emptyMap()
        }
    }

    // ── MESSAGING ────────────────────────────────────────────────────────────

    /**
     * Extracts contact and message body.
     *
     * Supported patterns (lowercase input assumed):
     *   "tell <contact> <body>"
     *   "message <contact> saying <body>"
     *   "tell <contact> that <body>"
     *   "send whatsapp to <contact> saying <body>"
     *   "let <contact> know <body>"
     *   "ask <contact> if/whether <body>"
     *   "say <body> to <contact>"
     *   "inform <contact> <body>"
     *
     * body is optional — if only a contact is found, "message" is omitted
     * from the result map, consistent with how downstream confirmation
     * prompts already handle a missing body.
     */
    private fun extractMessaging(text: String): Map<String, String> {
        val t = text.lowercase()
        val result = mutableMapOf<String, String>()

        // "say <body> to <contact>" — body comes before contact
        val reSayTo = Regex("^say\\s+(.+)\\s+to\\s+(\\S+)$")
        reSayTo.find(t)?.let { m ->
            result["contact"] = m.groupValues[2].trim()
            result["message"] = m.groupValues[1].trim()
            return result
        }

        // "let <contact> know <body>" / "let <contact> know"
        val reLetKnow = Regex("^let\\s+(\\S+)\\s+know(?:\\s+(.+))?$")
        reLetKnow.find(t)?.let { m ->
            result["contact"] = m.groupValues[1].trim()
            val body = m.groupValues[2].trim()
            if (body.isNotBlank()) result["message"] = body
            return result
        }

        // "ask <contact> if/whether <body>"
        val reAskIf = Regex("^ask\\s+(\\S+)\\s+(?:if|whether)\\s+(.+)$")
        reAskIf.find(t)?.let { m ->
            result["contact"] = m.groupValues[1].trim()
            result["message"] = m.groupValues[2].trim()
            return result
        }

        // General verb-contact-delimiter-body pattern
        // Covers: tell/message/text/inform/send.../whatsapp + optional "to"
        val reVerbBody = Regex(
            "^(?:tell|message|text|inform|whatsapp|send(?:\\s+(?:sms|whatsapp|a text|message))?)" +
            "(?:\\s+to)?\\s+(\\S+)(?:\\s+(?:saying|that|with message|:)\\s*(.+))?$"
        )
        reVerbBody.find(t)?.let { m ->
            result["contact"] = m.groupValues[1].trim()
            val body = m.groupValues[2].trim()
            if (body.isNotBlank()) result["message"] = body
            return result
        }

        return result
    }

    // ── MUSIC ────────────────────────────────────────────────────────────────

    /**
     * Extracts song title and optional app (spotify, youtube, etc.).
     *
     * Pattern: "play <song> [on <app>]" / "listen to <song> [on <app>]"
     * If "on <app>" is present, it's extracted; otherwise "app" is omitted.
     */
    private fun extractMusic(text: String): Map<String, String> {
        val t = text.lowercase()
        val result = mutableMapOf<String, String>()

        val rePlay = Regex("^(?:play|listen to)\\s+(.+?)(?:\\s+on\\s+(\\S+))?$")
        rePlay.find(t)?.let { m ->
            val song = m.groupValues[1].trim()
            val app  = m.groupValues[2].trim()
            if (song.isNotBlank()) result["song"] = song
            if (app.isNotBlank())  result["app"]  = app
        }

        return result
    }

    // ── REMINDER ─────────────────────────────────────────────────────────────

    /**
     * Extracts reminder duration as a human-readable string.
     *
     * Patterns: "in X minutes/hours/seconds" / "after X ..."
     * Returns {"duration": "2 hours"} etc.
     * More complex patterns (absolute times, dates) are out of scope here.
     */
    private fun extractReminder(text: String): Map<String, String> {
        val t = text.lowercase()
        val result = mutableMapOf<String, String>()

        val reDuration = Regex("(?:in|after)\\s+(\\d+)\\s+(second|seconds|minute|minutes|hour|hours)")
        reDuration.find(t)?.let { m ->
            val amount = m.groupValues[1]
            val unit   = m.groupValues[2]
            result["duration"] = "$amount $unit"
        }

        return result
    }
}
