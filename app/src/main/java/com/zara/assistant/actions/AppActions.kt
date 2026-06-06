package com.zara.assistant.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
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

    // ── App launch ────────────────────────────────────────────────────────

    /** Layer 5.2: launch directly by resolved package — no duplicate resolution. */
    fun launchByPackage(pkg: String, displayName: String?): String =
        launchPackage(pkg, displayName ?: pkg)

    /** Fallback: resolve by name then launch. */
    fun openApp(name: String): String {
        val result = resolver.resolve(name.lowercase().trim(), getAppCache())
        return when {
            result.packageName != null -> launchPackage(result.packageName, result.displayLabel ?: name)
            result.candidates.isNotEmpty() -> {
                val list = result.candidates.take(5).mapIndexed { i, s -> "${i+1}. $s" }.joinToString(", ")
                "Did you mean: $list?"
            }
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
            if (number != null) "Opening SMS to $contact." else "Opening SMS. Couldn't resolve '$contact'."
        } catch (e: Exception) {
            ZaraLogger.e("sendSms: ${e.message}")
            "Couldn't open SMS app."
        }
    }

    suspend fun sendWhatsApp(contact: String, body: String): String {
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

    // ── Camera / Alarm / Timer ────────────────────────────────────────────

    fun openCamera(): String {
        return try {
            context.startActivity(
                Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "Opening camera."
        } catch (e: Exception) { "Couldn't open camera." }
    }

    fun openAlarm(): String {
        return try {
            context.startActivity(
                Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "Opening clock."
        } catch (e: Exception) { "Couldn't open clock." }
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

    /**
     * Layer 5.2: preferredPackage (resolved) takes priority over preferredApp (name).
     * Falls back through: package → app name → generic geo URI.
     */
    fun navigateTo(
        destination: String,
        preferredPackage: String? = null,
        preferredApp: String? = null
    ): String {
        return try {
            // Build URI based on known app name/package
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
            // If resolved package known, restrict to it
            if (preferredPackage != null) intent.setPackage(preferredPackage)
            if (context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()) {
                context.startActivity(intent)
                return "Navigating to $destination${if (preferredApp != null) " on $preferredApp" else ""}."
            }
            // Fallback: generic geo without package restriction
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(destination)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
            "Navigating to $destination."
        } catch (e: Exception) {
            ZaraLogger.e("navigateTo: ${e.message}")
            "Couldn't open navigation."
        }
    }

    // ── Music ─────────────────────────────────────────────────────────────

    /**
     * Layer 5.2: launch resolved package directly, then search within it if possible.
     * Falls back to playMusic() if package can't handle the search URI.
     */
    fun playMusicByPackage(pkg: String, appName: String?, query: String?): String {
        return try {
            // Try deep-link search first (Spotify supports this)
            if (query != null) {
                val searchUri = when {
                    pkg.contains("spotify") -> Uri.parse("spotify:search:$query")
                    else -> null
                }
                if (searchUri != null) {
                    val intent = Intent(Intent.ACTION_VIEW, searchUri)
                        .setPackage(pkg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()) {
                        context.startActivity(intent)
                        return "Playing $query on ${appName ?: pkg}."
                    }
                }
            }
            // Fallback: launch app directly
            launchPackage(pkg, appName ?: pkg)
        } catch (e: Exception) {
            ZaraLogger.e("playMusicByPackage: ${e.message}")
            playMusic(query, appName)
        }
    }

    /** Fallback music play by app name. */
    fun playMusic(query: String?, app: String? = null): String {
        val appLower = app?.lowercase()
        return try {
            if (appLower == null || appLower.contains("spotify")) {
                if (query != null) {
                    val spotifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$query"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (context.packageManager.queryIntentActivities(spotifyIntent, 0).isNotEmpty()) {
                        context.startActivity(spotifyIntent)
                        return "Playing $query on Spotify."
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
            "Couldn't open music app."
        }
    }
}
