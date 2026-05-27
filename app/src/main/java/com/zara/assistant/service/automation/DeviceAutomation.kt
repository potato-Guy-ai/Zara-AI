package com.zara.assistant.service.automation

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import com.zara.assistant.utils.ZaraLogger

class DeviceAutomation(private val context: Context) {

    enum class VolumeDir { UP, DOWN }

    // ── Wi-Fi ──────────────────────────────────────────────────
    fun setWifi(enable: Boolean) {
        try {
            // Android 10+: direct toggle removed, guide user to settings
            val intent = Intent(Settings.Panel.ACTION_WIFI).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback for older API
            @Suppress("DEPRECATION")
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wm.isWifiEnabled = enable
        }
        ZaraLogger.d("Wifi set: $enable")
    }

    // ── Bluetooth ──────────────────────────────────────────────
    fun setBluetooth(enable: Boolean) {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = bm.adapter
        if (enable) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } else {
            @Suppress("DEPRECATION")
            adapter?.disable()
        }
        ZaraLogger.d("Bluetooth set: $enable")
    }

    // ── Flashlight ─────────────────────────────────────────────
    fun setFlashlight(enable: Boolean) {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cm.cameraIdList[0]
            cm.setTorchMode(cameraId, enable)
            ZaraLogger.d("Flashlight: $enable")
        } catch (e: Exception) {
            ZaraLogger.e("Flashlight error: ${e.message}")
        }
    }

    // ── Volume ─────────────────────────────────────────────────
    fun adjustVolume(dir: VolumeDir) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val flag = if (dir == VolumeDir.UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, flag, AudioManager.FLAG_SHOW_UI)
        ZaraLogger.d("Volume adjusted: $dir")
    }

    fun setVolume(level: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val scaled = (level.coerceIn(0, 100) * max / 100)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, scaled, AudioManager.FLAG_SHOW_UI)
    }

    // ── Silent Mode ────────────────────────────────────────────
    fun setSilentMode(silent: Boolean) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.ringerMode = if (silent) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
        ZaraLogger.d("Silent mode: $silent")
    }

    // ── Brightness ─────────────────────────────────────────────
    fun setBrightness(level: Int) {
        try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                level.coerceIn(0, 255)
            )
        } catch (e: Exception) {
            // Requires WRITE_SETTINGS permission
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    // ── Camera ─────────────────────────────────────────────────
    fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openVideoCamera() {
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    // ── App Launcher ───────────────────────────────────────────
    fun openApp(appName: String) {
        val pm = context.packageManager
        val packageName = resolvePackageName(appName.lowercase().trim())
        try {
            val intent = if (packageName != null) {
                pm.getLaunchIntentForPackage(packageName)
            } else {
                // Search installed apps by label
                pm.getInstalledApplications(0)
                    .firstOrNull { pm.getApplicationLabel(it).toString().lowercase().contains(appName.lowercase()) }
                    ?.let { pm.getLaunchIntentForPackage(it.packageName) }
            }
            intent?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(this)
            } ?: ZaraLogger.w("App not found: $appName")
        } catch (e: Exception) {
            ZaraLogger.e("Open app error: ${e.message}")
        }
    }

    private fun resolvePackageName(name: String): String? = when {
        name.contains("whatsapp") -> "com.whatsapp"
        name.contains("youtube") -> "com.google.android.youtube"
        name.contains("chrome") -> "com.android.chrome"
        name.contains("maps") -> "com.google.android.apps.maps"
        name.contains("spotify") -> "com.spotify.music"
        name.contains("instagram") -> "com.instagram.android"
        name.contains("telegram") -> "org.telegram.messenger"
        name.contains("gmail") -> "com.google.android.gm"
        name.contains("camera") -> null // use openCamera()
        name.contains("settings") -> "com.android.settings"
        name.contains("calculator") -> "com.android.calculator2"
        name.contains("calendar") -> "com.google.android.calendar"
        name.contains("clock") -> "com.google.android.deskclock"
        else -> null
    }

    // ── Alarm ──────────────────────────────────────────────────
    fun openAlarm() {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun setAlarm(hour: Int, minute: Int, label: String = "Zara Alarm") {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    // ── Lock / Reboot ──────────────────────────────────────────
    fun lockScreen() {
        ZaraAccessibilityService.instance?.lockScreen()
            ?: ZaraLogger.w("Accessibility service not bound for lock")
    }

    fun reboot() {
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot"))
        } catch (e: Exception) {
            ZaraLogger.e("Reboot requires root: ${e.message}")
        }
    }
}
