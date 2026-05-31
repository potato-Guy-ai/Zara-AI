package com.zara.assistant.core

/**
 * Strips PII before any cloud dispatch.
 * Sanitizes rawText, target, and all extra values.
 * isSafeForCloud() is now enforced inside IntentRouter before every cloud call.
 */
class PrivacyFilter {

    private val phoneRegex   = Regex("""\b[\d\s\-+().]{7,20}\b""")
    private val emailRegex   = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")
    private val aadhaarRegex = Regex("""\b\d{4}\s?\d{4}\s?\d{4}\b""")  // Indian ID
    private val panRegex     = Regex("""\b[A-Z]{5}[0-9]{4}[A-Z]\b""")

    /** Only CLOUD intents are permitted to reach cloud. */
    fun isSafeForCloud(intent: ZaraIntent): Boolean =
        intent.type == IntentType.CLOUD

    /** Sanitize the rawText string only. */
    fun sanitize(text: String): String = text
        .replace(phoneRegex,   "[NUMBER]")
        .replace(emailRegex,   "[EMAIL]")
        .replace(aadhaarRegex, "[ID]")
        .replace(panRegex,     "[ID]")

    /**
     * Sanitize all user-facing fields of a ZaraIntent before cloud dispatch.
     * target and extra values may contain contact names or message bodies.
     */
    fun sanitizeIntent(intent: ZaraIntent): String {
        val parts = mutableListOf<String>()
        parts += sanitize(intent.rawText)
        // Do NOT forward target (contact name) or extra[body] (SMS content) to cloud
        // Only the sanitized query text is sent
        return parts.joinToString(" ").trim()
    }
}
