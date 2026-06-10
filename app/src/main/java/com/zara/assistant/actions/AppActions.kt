package com.zara.assistant.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import com.zara.assistant.utils.ZaraLogger

/**
 * B2.2: Added setAlarm(), showAlarms(), showTimers(), openClock().
 */
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

    // ── App launch ────────────────────────────────────────────────────────

    fun launchByPackage(pkg: String, displayName: String?): String =
        launchPackage(pkg, displayName ?: pkg)

    fun openApp(name: String): String {
        val result = resolver.resolve(name.lowercase().trim(), getAppCache())
        return when {
            result.packageName != null -> launchPackage(result.packageName, result.displayLabel ?: name)
            result.candidates.isNotEmpty() -> {
                val list = result.candidates.take(5).mapIndexed { i, s -> "${i+1}. $s" }.joinToString(", ")
                "Did you mean: $list?"
            }
            else -> "I couldn\'t find an installed app called \'$name\'."
        }
    }

    private fun launchPackage(pkg: String, displayName: String): String {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: return "\'$displayName\' is installed but can\'t be launched."
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening $displayName."
        } catch (e: Exception) {
            ZaraLogger.e("launchPackage: ${e.message}")
            "Couldn\'t open $displayName."
        }
    }

    // ── SMS / WhatsApp ────────────────────────────────────────────────────

    suspend fun sendSms(contact: String, body: String): String {
        val number = ContactResolver(context).resolveNumber(contact)
        return try {
            val uri = if (number != null) Uri.parse("smsto:$number") else Uri.parse("smsto:")
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = uri
                putExtra("sms_body", body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            if (number != null) "Opening SMS to $contact." else "Opening SMS. Couldn\'t resolve \'$contact\'."
        } catch (e: Exception) {
            ZaraLogger.e("sendSms: ${e.message}")
            "Couldn\'t open SMS app."
        }
    }

    suspend fun sendWhatsApp(contact: String, body: String): String {
        val number = ContactResolver(context).resolveNumber(contact)
            ?: return "I couldn\'t find \'$contact\' in your contacts to WhatsApp."
        val cleaned = number.filter { it.isDigit() }
        return try {
            val uri = Uri.parse("https://wa.me/$cleaned?text=${Uri.encode(body)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening WhatsApp to message $contact."
        } catch (e: Exception) {
            ZaraLogger.e("sendWhatsApp: ${e.message}")
            "Couldn\'t open WhatsApp. Make sure it\'s installed."
        }
    }

    // ── Camera ────────────────────────────────────────────────────────────

    fun openCamera(): String {
        return try {
            context.startActivity(
                Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "Opening camera."
        } catch (e: Exception) { "Couldn\'t open camera." }
    }

    // ── Alarm / Timer / Clock ─────────────────────────────────────────────

    /**
     * B2.2: Set a specific alarm using AlarmClock.ACTION_SET_ALARM.
     * hour: 0-23, minute: 0-59.
     */
    fun setAlarm(hour: Int, minute: Int): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val ampm  = if (hour < 12) "AM" else "PM"
            val h12   = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
            val minStr = minute.toString().padStart(2, '0')
            "Alarm set for $h12:$minStr $ampm."
        } catch (e: Exception) {
            ZaraLogger.e("setAlarm: ${e.message}")
            openAlarm()
        }
    }

    /** B2.2: Show existing alarms list. */
    fun showAlarms(): String {
        return try {
            context.startActivity(
                Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "Here are your alarms."
        } catch (e: Exception) { "Couldn\'t open alarms."
        }
    }

    /** B2.2: Show timer screen (ACTION_SHOW_TIMERS API 26+, fallback to SHOW_ALARMS). */
    fun showTimers(): String {
        return try {
            val intent = if (android.os.Build.VERSION.SDK_INT >= 26) {
                Intent(AlarmClock.ACTION_SHOW_TIMERS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Here are your timers."
        } catch (e: Exception) { "Couldn\'t open timers." }
    }

    /** B2.2: Open the clock app. */
    fun openClock(): String {
        return try {
            context.startActivity(
                Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "Opening clock."
        } catch (e: Exception) { "Couldn\'t open clock." }
    }

    /** Legacy: open alarm list (used as fallback). */
    fun openAlarm(): String {
        return try {
            context.startActivity(
                Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "Opening clock."
        } catch (e: Exception) { "Couldn\'t open clock." }
    }

    fun setTimer(seconds: Long): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds.toInt())
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Timer set for ${formatDuration(seconds)}."
        } catch (e: Exception) {
            ZaraLogger.e("setTimer: ${e.message}")
            openAlarm()
        }
    }

    private fun formatDuration(seconds: Long): String = when {
        seconds % 3600 == 0L -> "${seconds / 3600} hour${if (seconds / 3600 > 1) "s" else ""}"
        seconds % 60   == 0L -> "${seconds / 60} minute${if (seconds / 60 > 1) "s" else ""}"
        else                 -> "$seconds seconds"
    }

    // ── Navigation ────────────────────────────────────────────────────────

    fun navigateTo(destination: String, preferredPackage: String? = null, preferredApp: String? = null): String {
        return try {
            val appHint = preferredApp?.lowercase()
            val uri = when {
                preferredPackage == "com.google.android.apps.maps" ||
                appHint?.contains("google maps") == true || appHint?.contains("maps") == true ->
                    Uri.parse("google.navigation:q=${Uri.encode(destination)}")
                preferredPackage == "com.waze" || appHint?.contains("waze") == true ->
                    Uri.parse("waze://?q=${Uri.encode(destination)}&navigate=yes")
                else -> Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (preferredPackage != null) intent.setPackage(preferredPackage)
            if (context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()) {
                context.startActivity(intent)
                return "Navigating to $destination${if (preferredApp != null) " on $preferredApp" else ""}."
            }
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(destination)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
            "Navigating to $destination."
        } catch (e: Exception) {
            ZaraLogger.e("navigateTo: ${e.message}")
            "Couldn\'t open navigation."
        }
    }

    // ── Music ─────────────────────────────────────────────────────────────

    fun playMusicByPackage(pkg: String, appName: String?, query: String?): String {
        return try {
            if (query != null) {
                val searchUri = when {
                    pkg.contains("spotify") -> Uri.parse("spotify:search:${Uri.encode(query)}")
                    pkg.contains("youtube") -> Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
                    else -> null
                }
                if (searchUri != null) {
                    val intent = Intent(Intent.ACTION_VIEW, searchUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (pkg.contains("spotify")) intent.setPackage(pkg)
                    try {
                        context.startActivity(intent)
                        return "Playing $query on ${appName ?: pkg}."
                    } catch (ignored: Exception) {
                        ZaraLogger.d("playMusicByPackage: deep-link failed for $pkg, falling back")
                    }
                }
            }
            launchPackage(pkg, appName ?: pkg)
        } catch (e: Exception) {
            ZaraLogger.e("playMusicByPackage: ${e.message}")
            playMusic(query, appName)
        }
    }

    fun playMusic(query: String?, app: String? = null): String {
        val appLower = app?.lowercase()
        return try {
            if (appLower == null || appLower.contains("spotify")) {
                if (query != null) {
                    try {
                        val spotifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(query)}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(spotifyIntent)
                        return "Playing $query on Spotify."
                    } catch (ignored: Exception) {
                        ZaraLogger.d("playMusic: Spotify URI failed, trying fallback")
                    }
                }
            }
            if (appLower != null && (appLower.contains("youtube music") || appLower.contains("yt music"))) {
                val ytmPkg = getAppCache()["youtube music"] ?: getAppCache()["yt music"]
                if (ytmPkg != null) {
                    val intent = context.packageManager.getLaunchIntentForPackage(ytmPkg)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (intent != null) {
                        context.startActivity(intent)
                        return if (query != null) "Playing $query on YouTube Music." else "Opening YouTube Music."
                    }
                }
            }
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            if (query != null) "Playing $query." else "Opening music."
        } catch (e: Exception) {
            ZaraLogger.e("playMusic: ${e.message}")
            "Couldn\'t open music app."
        }
    }

    // ── Search ────────────────────────────────────────────────────────────

    fun searchYouTube(query: String): String {
        return try {
            val ytPkg = getAppCache()["youtube"]
            if (ytPkg != null) {
                try {
                    val intent = Intent(Intent.ACTION_SEARCH,
                        Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}"))
                        .setPackage(ytPkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return "Searching YouTube for $query."
                } catch (ignored: Exception) { }
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(browserIntent)
                    return "Searching YouTube for $query."
                } catch (ignored: Exception) { }
            }
            val browserIntent = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(browserIntent)
            "Searching YouTube for $query."
        } catch (e: Exception) {
            ZaraLogger.e("searchYouTube: ${e.message}")
            "Couldn\'t search YouTube."
        }
    }

    fun search(query: String, app: String? = null): String {
        val appLower = app?.lowercase()
        return when {
            appLower != null && (appLower.contains("youtube") || appLower == "yt") -> searchYouTube(query)
            else -> {
                try {
                    val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "Searching for $query."
                } catch (e: Exception) {
                    ZaraLogger.e("search: ${e.message}")
                    "Couldn\'t perform search."
                }
            }
        }
    }
}
