package com.zara.assistant.core.messaging

import com.zara.assistant.core.ChannelType

/**
 * Layer 6.7 — MessageNLU.
 *
 * Parser-only component (architecture-locked, Phase 1 approval):
 * MessageNLU NEVER constructs a ZaraIntent. It only extracts a structured
 * [MessageParseResult] from already-normalized text. LocalIntentClassifier
 * remains the sole place that builds ZaraIntent (via its existing action()
 * helper), keeping intent construction centralized.
 *
 * Deterministic-first only — no semantic/ML fallback in this phase.
 *
 * Supported casual-phrasing patterns (fallback, after the existing
 * command-style regex parser in LocalIntentClassifier has already failed
 * to extract a usable intent):
 *   A) tell X body            e.g. "tell amma i'll be late"
 *   B) let X know body        e.g. "let dad know i reached"
 *   C) ask X if/whether body  e.g. "ask mom if food is ready"
 *      (Phase 1 constraint: ONLY "ask X if ..." / "ask X whether ..." —
 *       generic "ask X ..." is NOT supported, to avoid false positives
 *       like "ask what time it is" / "ask who won".)
 *   D) say body to X          e.g. "say happy birthday to sister"
 *   E) inform X body          e.g. "inform dad i reached safely"
 *
 * Cheap trigger gate: text must start with tell/let/ask/say/inform, or
 * parse() returns null immediately without running any regex — keeps the
 * non-messaging majority of utterances (which already exit earlier in
 * LocalIntentClassifier anyway) effectively free of any cost here too.
 *
 * Known limitation (documented, not solved in this phase): contact names
 * are captured as a single token (\S+). Multi-word names ("my mom",
 * "uncle raj") are out of scope for Phase 1 — same single-token assumption
 * the existing command-style parser already makes for its fallback path.
 *
 * Channel rule (architecture-locked): explicit "whatsapp" -> SEND_WHATSAPP,
 * explicit "sms"/"text message" -> SEND_SMS, otherwise default to
 * SEND_WHATSAPP (note: this differs from the old command-style parser's
 * default of SMS — an accepted, explicitly-specified inconsistency between
 * the two paths, flagged in the Phase 1 audit).
 *
 * Patches:
 *   Bug 1: trailing punctuation stripped from contact only (not body).
 *   Bug 3: contact length <= 2 yields LOW confidence.
 */
object MessageNLU {

    private val TRIGGER_WORDS = setOf("tell", "let", "ask", "say", "inform")

    // A) tell X body
    private val reTell     = Regex("^tell\\s+(\\S+)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val reTellBare = Regex("^tell\\s+(\\S+)$", RegexOption.IGNORE_CASE)

    // B) let X know body
    private val reLetKnow     = Regex("^let\\s+(\\S+)\\s+know\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val reLetKnowBare = Regex("^let\\s+(\\S+)\\s+know$", RegexOption.IGNORE_CASE)

    // C) ask X if/whether body — STRICT per Phase 1 approval, no generic "ask X ..."
    private val reAskIf     = Regex("^ask\\s+(\\S+)\\s+(?:if|whether)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val reAskIfBare = Regex("^ask\\s+(\\S+)\\s+(?:if|whether)$", RegexOption.IGNORE_CASE)

    // D) say body to X
    private val reSayTo = Regex("^say\\s+(.+)\\s+to\\s+(\\S+)$", RegexOption.IGNORE_CASE)

    // E) inform X body
    private val reInform     = Regex("^inform\\s+(\\S+)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val reInformBare = Regex("^inform\\s+(\\S+)$", RegexOption.IGNORE_CASE)

    private val AMBIGUOUS_CONTACTS = setOf("him", "her", "them", "someone", "somebody")

    // Bug 1: strip trailing punctuation from contact token only. Body is never passed here.
    private fun cleanContact(raw: String): String = raw.trimEnd(',', '.', '!', '?', ';', ':')

    /**
     * [normalizedText] must already be lowercased+trimmed (same convention
     * LocalIntentClassifier uses internally — avoids re-normalizing).
     * Returns null if the cheap trigger gate fails or no pattern matches.
     */
    fun parse(normalizedText: String): MessageParseResult? {
        val t = normalizedText.trim()
        val firstWord = t.substringBefore(' ')
        if (firstWord !in TRIGGER_WORDS) return null

        return when (firstWord) {
            "tell"   -> matchVerbContactBody(t, reTell, reTellBare)
            "let"    -> matchVerbContactBody(t, reLetKnow, reLetKnowBare)
            "ask"    -> matchVerbContactBody(t, reAskIf, reAskIfBare)
            "say"    -> matchSayTo(t)
            "inform" -> matchVerbContactBody(t, reInform, reInformBare)
            else     -> null
        }
    }

    /** Shared shape: verb [+connector] CONTACT BODY, with a bare CONTACT-only fallback. */
    private fun matchVerbContactBody(text: String, withBody: Regex, bareContact: Regex): MessageParseResult? {
        withBody.find(text)?.let { m ->
            val contact = cleanContact(m.groupValues[1].trim())  // Bug 1
            val body = m.groupValues[2].trim()
            if (contact.isNotBlank() && body.isNotBlank()) {
                return MessageParseResult(
                    contact = contact,
                    body = body,
                    channel = detectChannel(text),
                    confidence = confidenceFor(contact)
                )
            }
        }
        bareContact.find(text)?.let { m ->
            val contact = cleanContact(m.groupValues[1].trim())  // Bug 1
            if (contact.isNotBlank()) {
                return MessageParseResult(
                    contact = contact,
                    body = null,
                    channel = detectChannel(text),
                    confidence = MessageConfidence.MEDIUM
                )
            }
        }
        return null
    }

    /** D) say body to X — body comes before the contact, so it has its own shape. */
    private fun matchSayTo(text: String): MessageParseResult? {
        reSayTo.find(text)?.let { m ->
            val body = m.groupValues[1].trim()
            val contact = cleanContact(m.groupValues[2].trim())  // Bug 1
            if (contact.isNotBlank() && body.isNotBlank()) {
                return MessageParseResult(
                    contact = contact,
                    body = body,
                    channel = detectChannel(text),
                    confidence = confidenceFor(contact)
                )
            }
        }
        return null
    }

    private fun confidenceFor(contact: String): MessageConfidence = when {
        contact.length <= 2                            -> MessageConfidence.LOW   // Bug 3
        contact.lowercase() in AMBIGUOUS_CONTACTS      -> MessageConfidence.LOW
        else                                           -> MessageConfidence.HIGH
    }

    private fun detectChannel(text: String): String {
        val t = text.lowercase()
        return when {
            t.contains("whatsapp") -> ChannelType.WHATSAPP
            t.contains("sms") || t.contains("text message") -> ChannelType.SMS
            else -> ChannelType.WHATSAPP // architecture-locked default for this parser
        }
    }
}
