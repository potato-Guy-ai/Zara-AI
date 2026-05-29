package com.zara.assistant.core

/**
 * Fast offline intent classifier.
 * Replaces the old ZaraAIEngine regex matcher.
 * Returns structured ZaraIntent instead of direct action execution.
 */
object LocalIntentClassifier {

    fun classify(text: String): ZaraIntent {
        val t = text.lowercase().trim()

        return when {
            // Calls
            t.matches(Regex(".*(call|ring|dial|phone).*")) -> {
                val target = extractAfter(t, "call|ring|dial|phone")
                ZaraIntent(IntentType.ACTION, "CALL", target, rawText = text)
            }
            t.contains("answer") && t.contains("call") ->
                ZaraIntent(IntentType.ACTION, "ANSWER_CALL", rawText = text)
            t.matches(Regex(".*(hang up|end call|reject call).*")) ->
                ZaraIntent(IntentType.ACTION, "END_CALL", rawText = text)

            // Messaging
            t.matches(Regex(".*(send|text|message).*to.*")) -> {
                val target = extractBetween(t, "to", "saying|that|with") ?: extractAfter(t, "to")
                val body = extractAfter(t, "saying|that|with")
                ZaraIntent(IntentType.ACTION, "SEND_SMS", target,
                    extra = if (body != null) mapOf("body" to body) else emptyMap(), rawText = text)
            }

            // Apps
            t.matches(Regex(".*(open|launch|start).*")) -> {
                val target = extractAfter(t, "open|launch|start")
                ZaraIntent(IntentType.ACTION, "OPEN_APP", target, rawText = text)
            }

            // Device
            t.contains("wifi") || t.contains("wi-fi") -> {
                val on = t.contains("on") || t.contains("enable")
                ZaraIntent(IntentType.ACTION, "SET_WIFI", extra = mapOf("on" to on.toString()), rawText = text)
            }
            t.contains("bluetooth") -> {
                val on = t.contains("on") || t.contains("enable")
                ZaraIntent(IntentType.ACTION, "SET_BLUETOOTH", extra = mapOf("on" to on.toString()), rawText = text)
            }
            t.contains("flashlight") || t.contains("torch") -> {
                val on = t.contains("on") || t.contains("enable")
                ZaraIntent(IntentType.ACTION, "SET_FLASHLIGHT", extra = mapOf("on" to on.toString()), rawText = text)
            }
            t.matches(Regex(".*(volume).*(up|down|max|low|mute).*")) -> {
                val dir = if (t.contains("up") || t.contains("max")) "up" else "down"
                ZaraIntent(IntentType.ACTION, "SET_VOLUME", extra = mapOf("dir" to dir), rawText = text)
            }
            t.contains("silent") || t.contains("mute") ->
                ZaraIntent(IntentType.ACTION, "SET_SILENT", extra = mapOf("on" to "true"), rawText = text)
            t.contains("lock") && t.contains("phone") ->
                ZaraIntent(IntentType.ACTION, "LOCK_SCREEN", rawText = text)
            t.contains("take photo") || t.contains("open camera") ->
                ZaraIntent(IntentType.ACTION, "OPEN_CAMERA", rawText = text)
            t.matches(Regex(".*(set|create).*(alarm|timer).*")) ->
                ZaraIntent(IntentType.ACTION, "SET_ALARM", rawText = text)

            // Conversation
            t.matches(Regex(".*(what time|current time|time now).*")) ->
                ZaraIntent(IntentType.CONVERSATION, "TIME", rawText = text)
            t.matches(Regex(".*(what.*date|today.*date|what day).*")) ->
                ZaraIntent(IntentType.CONVERSATION, "DATE", rawText = text)
            t.matches(Regex(".*(how are you|you okay|you good).*")) ->
                ZaraIntent(IntentType.CONVERSATION, "GREETING", rawText = text)
            t.matches(Regex(".*(stop|goodbye|bye|sleep|shut up).*")) ->
                ZaraIntent(IntentType.CONVERSATION, "STOP", rawText = text)

            // Cloud fallback for complex queries
            t.length > 20 && !t.matches(Regex(".*(open|call|send|set|turn).*")) ->
                ZaraIntent(IntentType.CLOUD, "QUERY", rawText = text)

            else -> ZaraIntent(IntentType.UNKNOWN, "UNKNOWN", rawText = text)
        }
    }

    private fun extractAfter(text: String, keywords: String): String? =
        Regex("(?:$keywords)\\s+(.+)").find(text)?.groupValues?.getOrNull(1)?.trim()

    private fun extractBetween(text: String, after: String, before: String): String? =
        Regex("(?:$after)\\s+(.+?)\\s+(?:$before)").find(text)?.groupValues?.getOrNull(1)?.trim()
}
