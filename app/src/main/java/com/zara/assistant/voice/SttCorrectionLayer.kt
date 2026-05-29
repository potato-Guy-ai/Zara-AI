package com.zara.assistant.voice

/**
 * Contextual correction layer.
 * Corrects STT errors for Tanglish, app names, contact names.
 * Optimizes for INTENT accuracy, not transcript perfection.
 */
class SttCorrectionLayer {

    // App name corrections (expand as needed)
    private val appCorrections = mapOf(
        "what's up" to "whatsapp",
        "whats up" to "whatsapp",
        "what sup" to "whatsapp",
        "you tube" to "youtube",
        "u tube" to "youtube",
        "face book" to "facebook",
        "insta" to "instagram",
        "snap" to "snapchat",
        "tele gram" to "telegram",
        "maps" to "google maps",
        "gps" to "google maps"
    )

    // Common Tanglish/Hinglish phonetic corrections
    private val phoneticCorrections = mapOf(
        "amma" to "amma",
        "armor" to "amma",    // "call armor" → "call amma"
        "anna" to "anna",
        "akka" to "akka",
        "thatha" to "thatha",
        "paati" to "paati",
        "yaar" to "yaar"
    )

    fun correct(raw: String): String {
        if (raw.isBlank()) return raw
        var result = raw.lowercase().trim()

        // App name corrections
        appCorrections.forEach { (wrong, right) ->
            result = result.replace(wrong, right)
        }

        // Phonetic contact corrections (only in call/message context)
        if (result.contains("call ") || result.contains("message ") || result.contains("text ")) {
            phoneticCorrections.forEach { (wrong, right) ->
                result = result.replace(" $wrong", " $right")
            }
        }

        return result
    }
}
