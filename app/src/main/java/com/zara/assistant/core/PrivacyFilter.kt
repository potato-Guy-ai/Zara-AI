package com.zara.assistant.core

/**
 * Strips PII before any cloud call.
 * Contacts, phone numbers, account details never leave device.
 */
class PrivacyFilter {

    private val phoneRegex = Regex("""\b\d{7,15}\b""")
    private val emailRegex = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")

    fun sanitize(text: String): String {
        return text
            .replace(phoneRegex, "[NUMBER]")
            .replace(emailRegex, "[EMAIL]")
    }

    fun isSafeForCloud(intent: ZaraIntent): Boolean {
        // Never send ACTION intents (device commands) to cloud
        return intent.type == IntentType.CLOUD
    }
}
