package com.zara.assistant.actions

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import com.zara.assistant.utils.ZaraLogger

class AppActions(private val context: Context) {

    /**
     * Cached app list — built once on first openApp() call.
     * Maps lowercase display label → package name.
     * Avoids O(n) PackageManager scan on every launch.
     */
    private var appCache: Map<String, String>? = null

    private fun getAppCache(): Map<String, String> {
        appCache?.let { return it }
        val pm = context.packageManager
        val cache = pm.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || pm.getLaunchIntentForPackage(it.packageName) != null }
            .associate { info ->
                pm.getApplicationLabel(info).toString().lowercase().trim() to info.packageName
            }
        appCache = cache
        return cache
    }

    /**
     * Generic app launcher — no hardcoded package list.
     * Searches installed applications by display name.
     * Exact match preferred; falls back to contains match.
     * If multiple contains-matches, returns the shortest name match (closest).
     */
    fun openApp(name: String): String {
        val query = name.lowercase().trim()
        val cache = getAppCache()

        // 1. Exact label match
        val exactPkg = cache[query]
        if (exactPkg != null) return launchPackage(exactPkg, name)

        // 2. Starts-with match
        val startsWith = cache.entries.filter { it.key.startsWith(query) }
        if (startsWith.size == 1) return launchPackage(startsWith[0].value, name)
        if (startsWith.size > 1) return askClarification(name, startsWith.map { it.key })

        // 3. Contains match
        val contains = cache.entries.filter { it.key.contains(query) }
        if (contains.size == 1) return launchPackage(contains[0].value, name)
        if (contains.size > 1) {
            // Pick shortest label (closest match) automatically if one is ≤2 words
            val best = contains.minByOrNull { it.key.length }
            if (best != null && best.key.split(" ").size <= 2) return launchPackage(best.value, best.key)
            return askClarification(name, contains.map { it.key })
        }

        return "I couldn't find an installed app called '$name'."
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
        val list = matches.take(5).joinToString(", ")
        return "Found multiple apps matching '$query': $list. Which one did you mean?"
    }

    /**
     * Send SMS — resolves contact name to number via ContactResolver.
     * Number is embedded in the URI so the SMS app pre-fills it.
     */
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

    /**
     * Send WhatsApp message — uses wa.me deep link with resolved number.
     * Never silently falls back to SMS.
     */
    fun sendWhatsApp(contact: String, body: String): String {
        val number = ContactResolver(context).resolveNumber(contact)
            ?: return "I couldn't find '$contact' in your contacts to WhatsApp."
        // Strip non-digits except leading +
        val cleaned = number.filter { it.isDigit() }
        return try {
            val encodedBody = Uri.encode(body)
            val uri = Uri.parse("https://wa.me/$cleaned?text=$encodedBody")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening WhatsApp to message $contact."
        } catch (e: Exception) {
            ZaraLogger.e("sendWhatsApp: ${e.message}")
            "Couldn't open WhatsApp. Make sure it's installed."
        }
    }

    fun openCamera(): String {
        return try {
            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening camera."
        } catch (e: Exception) { "Couldn't open camera." }
    }

    fun openAlarm(): String {
        return try {
            val intent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening clock."
        } catch (e: Exception) { "Couldn't open clock." }
    }

    fun navigateTo(destination: String): String {
        return try {
            val uri = Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Navigating to $destination."
        } catch (e: Exception) {
            ZaraLogger.e("navigateTo: ${e.message}")
            "Couldn't open navigation."
        }
    }

    fun playMusic(query: String?): String {
        return try {
            val intent = if (query != null) {
                // Try any music app that handles search
                Intent(Intent.ACTION_SEARCH).apply {
                    putExtra(android.app.SearchManager.QUERY, query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // Prefer Spotify if installed
                    val spotifyIntent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("spotify:search:$query"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (context.packageManager.queryIntentActivities(spotifyIntent, 0).isNotEmpty()) {
                        return launchPackage("com.spotify.music", "Spotify").also {
                            // open spotify search
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("spotify:search:$query"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.let { "Playing $query on Spotify." }
                    }
                    Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$query"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MUSIC)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            if (query != null) "Playing $query." else "Opening music."
        } catch (e: Exception) {
            ZaraLogger.e("playMusic: ${e.message}")
            "Couldn't open music app."
        }
    }
}
