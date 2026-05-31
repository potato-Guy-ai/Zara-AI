package com.zara.assistant.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.zara.assistant.utils.ZaraLogger

class AppActions(private val context: Context) {

    private val packageMap = mapOf(
        "whatsapp"    to "com.whatsapp",
        "youtube"     to "com.google.android.youtube",
        "instagram"   to "com.instagram.android",
        "facebook"    to "com.facebook.katana",
        "twitter"     to "com.twitter.android",
        "x"           to "com.twitter.android",
        "telegram"    to "org.telegram.messenger",
        "snapchat"    to "com.snapchat.android",
        "spotify"     to "com.spotify.music",
        "netflix"     to "com.netflix.mediaclient",
        "maps"        to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps",
        "gmail"       to "com.google.android.gm",
        "chrome"      to "com.android.chrome",
        "settings"    to "com.android.settings",
        "camera"      to "com.android.camera",
        "calculator"  to "com.android.calculator2",
        "clock"       to "com.android.deskclock",
        "zomato"      to "com.application.zomato",
        "swiggy"      to "in.swiggy.android",
        "phonepe"     to "com.phonepe.app",
        "gpay"        to "com.google.android.apps.nbu.paisa.user",
        "paytm"       to "net.one97.paytm"
    )

    fun openApp(name: String): String {
        val key = name.lowercase().trim()
        val pkg = packageMap[key] ?: findByQuery(key) ?: return "Couldn't find app: $name"
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: return "$name is not installed."
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            "Opening $name"
        } catch (e: Exception) {
            ZaraLogger.e("openApp: ${e.message}")
            "Couldn't open $name"
        }
    }

    fun sendSms(contact: String, body: String): String {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:")
                putExtra("sms_body", body)
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

    fun navigateTo(destination: String): String {
        return try {
            val uri = Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Navigating to $destination"
        } catch (e: Exception) {
            ZaraLogger.e("navigateTo: ${e.message}")
            "Couldn't open navigation"
        }
    }

    fun playMusic(query: String?): String {
        return try {
            val intent = if (query != null) {
                Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$query")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?: Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_APP_MUSIC)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            }
            context.startActivity(intent)
            if (query != null) "Playing $query" else "Opening music"
        } catch (e: Exception) {
            ZaraLogger.e("playMusic: ${e.message}")
            "Couldn't open music app"
        }
    }

    private fun findByQuery(name: String): String? {
        return context.packageManager.getInstalledApplications(0)
            .firstOrNull {
                context.packageManager.getApplicationLabel(it)
                    .toString().lowercase().contains(name)
            }?.packageName
    }
}
