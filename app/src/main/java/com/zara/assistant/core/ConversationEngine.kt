package com.zara.assistant.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Handles all CONVERSATION-type intents locally.
 * Extracted from IntentRouter to enforce single responsibility.
 * Uses java.time (thread-safe) — not SimpleDateFormat.
 */
class ConversationEngine {

    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val dateFmt = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())

    fun handle(intent: ZaraIntent): String = when (intent.action) {
        IntentAction.TIME     -> "It's ${LocalTime.now().format(timeFmt)}"
        IntentAction.DATE     -> "Today is ${LocalDate.now().format(dateFmt)}"
        IntentAction.GREETING -> "I'm here. What do you need?"
        IntentAction.STOP     -> "Going quiet. Say Hey Zara to wake me."
        else                  -> "I'm still learning that one."
    }
}
