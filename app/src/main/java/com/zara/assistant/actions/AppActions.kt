package com.zara.assistant.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.zara.assistant.utils.ZaraLogger

class AppActions(private val context: Context) {

    private val packageMap = mapOf(
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "instagram" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "twitter" to "com.twitter.android",
        "x" to "com.twitter.android",
        "telegram" to "org.telegram.messenger",
        "snapchat" to "com.snapchat.android",
        "spotify" to "com.spotify.music",
        "netflix" to "com.netflix.mediaclient",
        "maps" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps",
        "gmail" to "com.google.android.gm",
        "chrome" to "com.android.chrome",
        "settings" to "com.android.settings",
        "camera" to "com.android.camera",
        "calculator" to "com.android.calculator2",
        "clock" to "com.android.deskclock"
    )

    fun openApp(name: String): String {
        val pkg = packageMap[name.lowercase().trim()]
            ?: findByQuery(name)
            ?: return "Couldn't find app: $name"
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: return "$name is not installed."
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening $name"
        } catch (e: Exception) {
            ZaraLogger.e("openApp error: ${e.message}")
            "Couldn't open $name"
        }
    }

    fun sendSms(contact: String, body: String): String {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:")
                putExtra("sms_body", body)
                putExtra(Intent.EXTRA_TEXT, contact)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening SMS to $contact"
        } catch (e: Exception) {
            "Couldn't open SMS app"
        }
    }

    fun openCamera(): String {
        return try {
            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening camera"
        } catch (e: Exception) { "Couldn't open camera" }
    }

    fun openAlarm(): String {
        return try {
            val intent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening clock"
        } catch (e: Exception) { "Couldn't open clock" }
    }

    private fun findByQuery(name: String): String? {
        val pm = context.packageManager
        return pm.getInstalledApplications(0)
            .firstOrNull { pm.getApplicationLabel(it).toString().lowercase().contains(name.lowercase()) }
            ?.packageName
    }
}
