package com.zara.assistant.actions

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import com.zara.assistant.utils.ZaraLogger

class AppActions(private val context: Context) {

    private var appCache: Map<String, String>? = null
    private val resolver: AppResolver = RuleBasedAppResolver()

    private fun getAppCache(): Map<String, String> {
        appCache?.let { return it }
        val pm = context.packageManager
        val cache = mutableMapOf<String, String>()
        pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .forEach { info ->
                val label = pm.getApplicationLabel(info).toString().lowercase().trim()
                if (!cache.containsKey(label)) cache[label] = info.packageName
            }
        appCache = cache
        return cache
    }

    fun openApp(name: String): String {
        val query = name.lowercase().trim()
        val cache = getAppCache()
        val result = resolver.resolve(query, cache)

        return when {
            result.packageName != null -> launchPackage(result.packageName, result.displayLabel ?: name)
            result.candidates.isNotEmpty() -> askClarification(name, result.candidates)
            else -> "I couldn't find an installed app called '$name'."
        }
    }

    private fun launchPackage(pkg: String, displayName: String): String {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: return "'$displayName' is installed but can't be launched."
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening $displayName."
        } catch (e: Exception) {
            ZaraLogger.e("launchPackage: ${e.message}")
            "Couldn't open $displayName."
        }
    }

    private fun askClarification(query: String, matches: List<String>): String {
        val list = matches.take(5).mapIndexed { i, s -> "${i+1}. $s" }.joinToString(", ")
        return "Did you mean: $list?"
    }

    fun sendSms(contact: String, body: String): String {
        val number = ContactResolver(context).resolveNumber(contact)
        return try {
            val uri = if (number != null) Uri.parse("smsto:$number") else Uri.parse("smsto:")
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = uri
                putExtra("sms_body", body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            if (number != null) "Opening SMS to $contact." else "Opening SMS. Couldn't resolve '$contact'."
        } catch (e: Exception) {
            ZaraLogger.e("sendSms: ${e.message}")
            "Couldn't open SMS app."
        }
    }

    fun sendWhatsApp(contact: String, body: String): String {
        val number = ContactResolver(context).resolveNumber(contact)
            ?: return "I couldn't find '$contact' in your contacts to WhatsApp."
        val cleaned = number.filter { it.isDigit() }
        return try {
            val uri = Uri.parse("https://wa.me/$cleaned?text=${Uri.encode(body)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening WhatsApp to message $contact."
        } catch (e: Exception) {
            ZaraLogger.e("sendWhatsApp: ${e.message}")
            "Couldn't open WhatsApp. Make sure it's installed."
        }
    }

    fun openCamera(): String {
        return try {
            context.startActivity(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Opening camera."
        } catch (e: Exception) { "Couldn't open camera." }
    }

    fun openAlarm(): String {
        return try {
            context.startActivity(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Opening clock."
        } catch (e: Exception) { "Couldn't open clock." }
    }

    fun navigateTo(destination: String): String {
        return try {
            val uri = Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Navigating to $destination."
        } catch (e: Exception) {
            ZaraLogger.e("navigateTo: ${e.message}")
            "Couldn't open navigation."
        }
    }

    fun playMusic(query: String?): String {
        return try {
            val intent = if (query != null) {
                val spotifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$query"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (context.packageManager.queryIntentActivities(spotifyIntent, 0).isNotEmpty()) {
                    context.startActivity(spotifyIntent)
                    return "Playing $query on Spotify."
                }
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            if (query != null) "Playing $query." else "Opening music."
        } catch (e: Exception) {
            ZaraLogger.e("playMusic: ${e.message}")
            "Couldn't open music app."
        }
    }
}
