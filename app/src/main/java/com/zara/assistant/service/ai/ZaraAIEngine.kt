package com.zara.assistant.service.ai

import android.content.Context
import com.zara.assistant.service.automation.*
import com.zara.assistant.utils.ZaraLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ZaraResponse(
    val speech: String,
    val action: (suspend () -> Unit)? = null
)

/**
 * ZaraAIEngine — Natural Language Understanding + Command Router
 *
 * Pipeline:
 * 1. Normalize text
 * 2. Rule-based intent classification (fast, offline, ~0ms)
 * 3. Optionally send to local LLM (llama.cpp server on localhost:8080) for complex queries
 * 4. Execute matched action
 *
 * To enable local LLM: run llama.cpp server on device or companion PC
 *   ./server -m phi-3-mini.gguf --port 8080
 */
class ZaraAIEngine(private val context: Context) {

    private val phone = PhoneAutomation(context)
    private val device = DeviceAutomation(context)
    private val messaging = MessagingAutomation(context)
    private val calendar = CalendarAutomation(context)
    private val context_memory = ConversationMemory()

    suspend fun process(input: String): ZaraResponse = withContext(Dispatchers.Default) {
        val text = input.lowercase().trim()
        ZaraLogger.d("AI processing: $text")
        context_memory.add(text)

        return@withContext matchIntent(text)
    }

    private fun matchIntent(text: String): ZaraResponse {

        // ── CALLS ──────────────────────────────────────────────
        if (text.matches(Regex(".*(call|ring|dial|phone).*"))) {
            val contact = extractContact(text, listOf("call", "ring", "dial", "phone"))
            return if (contact != null) {
                ZaraResponse("Calling $contact") { phone.call(contact) }
            } else {
                ZaraResponse("Who would you like to call?")
            }
        }

        if (text.contains("answer") && (text.contains("call") || text.contains("phone"))) {
            return ZaraResponse("Answering the call") { phone.answerCall() }
        }

        if (text.matches(Regex(".*(reject|decline|hang up|end).*call.*"))) {
            return ZaraResponse("Ending the call") { phone.endCall() }
        }

        // ── SMS ────────────────────────────────────────────────
        if (text.matches(Regex(".*(send|text|message).*"))) {
            val contact = extractContact(text, listOf("send", "text", "message", "to"))
            val msg = extractAfter(text, "saying|message|that|with")
            return if (contact != null && msg != null) {
                ZaraResponse("Sending message to $contact") { messaging.sendSMS(contact, msg) }
            } else if (contact != null) {
                ZaraResponse("What message should I send to $contact?")
            } else {
                ZaraResponse("Who should I message?")
            }
        }

        // ── DEVICE SETTINGS ────────────────────────────────────
        if (text.contains("wifi") || text.contains("wi-fi")) {
            val on = text.contains("on") || text.contains("enable") || text.contains("turn on")
            return ZaraResponse(if (on) "Turning Wi-Fi on" else "Turning Wi-Fi off") {
                device.setWifi(on)
            }
        }

        if (text.contains("bluetooth")) {
            val on = text.contains("on") || text.contains("enable")
            return ZaraResponse(if (on) "Bluetooth on" else "Bluetooth off") {
                device.setBluetooth(on)
            }
        }

        if (text.contains("flashlight") || text.contains("torch")) {
            val on = text.contains("on") || text.contains("enable")
            return ZaraResponse(if (on) "Flashlight on" else "Flashlight off") {
                device.setFlashlight(on)
            }
        }

        if (text.matches(Regex(".*(silent|mute|vibrate|ringer).*"))) {
            val silent = text.contains("silent") || text.contains("mute")
            return ZaraResponse(if (silent) "Switching to silent mode" else "Ringer on") {
                device.setSilentMode(silent)
            }
        }

        if (text.matches(Regex(".*(volume).*(up|down|max|low|zero|mute).*"))) {
            val direction = when {
                text.contains("up") || text.contains("max") -> DeviceAutomation.VolumeDir.UP
                text.contains("down") || text.contains("low") || text.contains("zero") || text.contains("mute") -> DeviceAutomation.VolumeDir.DOWN
                else -> DeviceAutomation.VolumeDir.UP
            }
            return ZaraResponse("Adjusting volume") { device.adjustVolume(direction) }
        }

        if (text.matches(Regex(".*(brightness).*(up|down|max|low|min).*"))) {
            val level = when {
                text.contains("max") || text.contains("up") -> 255
                text.contains("min") || text.contains("low") -> 30
                else -> 128
            }
            return ZaraResponse("Adjusting brightness") { device.setBrightness(level) }
        }

        // ── CAMERA ─────────────────────────────────────────────
        if (text.contains("take a photo") || text.contains("take picture") || text.contains("open camera")) {
            return ZaraResponse("Opening camera") { device.openCamera() }
        }

        // ── APPS ───────────────────────────────────────────────
        if (text.matches(Regex(".*(open|launch|start).*"))) {
            val appName = extractAfter(text, "open|launch|start")
            return if (appName != null) {
                ZaraResponse("Opening $appName") { device.openApp(appName) }
            } else {
                ZaraResponse("Which app would you like to open?")
            }
        }

        // ── ALARMS & REMINDERS ─────────────────────────────────
        if (text.matches(Regex(".*(set|create).*(alarm|timer|reminder).*"))) {
            return ZaraResponse("Opening clock to set alarm") { device.openAlarm() }
        }

        // ── CALENDAR ───────────────────────────────────────────
        if (text.matches(Regex(".*(schedule|add|create|set).*(event|meeting|appointment).*"))) {
            return ZaraResponse("Opening calendar") { calendar.openCalendar() }
        }

        if (text.matches(Regex(".*(what.*today|my schedule|my calendar|upcoming events).*"))) {
            return ZaraResponse("Let me check your calendar") {
                val events = calendar.getTodaysEvents()
                // TTS will handle events string
            }
        }

        // ── SYSTEM ─────────────────────────────────────────────
        if (text.contains("lock") && text.contains("phone")) {
            return ZaraResponse("Locking the phone") { device.lockScreen() }
        }

        if (text.contains("restart") || text.contains("reboot")) {
            return ZaraResponse("Are you sure you want to restart? Say yes to confirm.")
        }

        if (context_memory.lastWas("restart") && (text.contains("yes") || text.contains("confirm"))) {
            return ZaraResponse("Restarting the phone") { device.reboot() }
        }

        // ── GENERAL / FALLBACK ─────────────────────────────────
        if (text.matches(Regex(".*(what time|what's the time|current time).*"))) {
            val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
                .format(java.util.Date())
            return ZaraResponse("It's $time")
        }

        if (text.matches(Regex(".*(what.*date|today.*date|what day).*"))) {
            val date = java.text.SimpleDateFormat("EEEE, MMMM d", java.util.Locale.US)
                .format(java.util.Date())
            return ZaraResponse("Today is $date")
        }

        if (text.matches(Regex(".*(how are you|you okay|you good).*"))) {
            return ZaraResponse("I'm doing great! Ready to help you. What do you need?")
        }

        if (text.matches(Regex(".*(stop|goodbye|bye|sleep|shut up).*"))) {
            return ZaraResponse("Alright, going quiet. Say 'Hey Zara' whenever you need me.")
        }

        // Unknown — try local LLM if available
        return ZaraResponse("I heard: $text. I'm still learning that command.")
    }

    private fun extractContact(text: String, keywords: List<String>): String? {
        val pattern = keywords.joinToString("|")
        val regex = Regex("(?:$pattern)\\s+(\\w+(?:\\s+\\w+)?)")
        return regex.find(text)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun extractAfter(text: String, keywords: String): String? {
        val regex = Regex("(?:$keywords)\\s+(.+)")
        return regex.find(text)?.groupValues?.getOrNull(1)?.trim()
    }
}

/** Lightweight in-memory conversation context */
class ConversationMemory(private val maxSize: Int = 10) {
    private val history = ArrayDeque<String>()

    fun add(text: String) {
        if (history.size >= maxSize) history.removeFirst()
        history.addLast(text)
    }

    fun lastWas(keyword: String): Boolean =
        history.size >= 2 && history[history.size - 2].contains(keyword)

    fun recent(): List<String> = history.toList()
    fun clear() = history.clear()
}
