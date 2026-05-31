package com.zara.assistant.actions

import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import com.zara.assistant.utils.ZaraLogger

class MediaActions(private val context: Context) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * C3 fix: WifiManager.setWifiEnabled() is a no-op on API 29+.
     * On API 29+ open the Wi-Fi settings panel so the user controls the toggle.
     * On API 26-28 (still supported by minSdk) use the deprecated API — it works there.
     * Never report a state change we cannot verify.
     */
    fun openWifiSettings(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+: open the Quick Settings Wi-Fi panel
                val intent = Intent(Settings.Panel.ACTION_WIFI)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Opening Wi-Fi settings."
            } else {
                // API 26-28: deprecated but functional
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Opening Wi-Fi settings."
            }
        } catch (e: Exception) {
            ZaraLogger.e("openWifiSettings error: ${e.message}")
            "Couldn't open Wi-Fi settings."
        }
    }

    /**
     * C4 fix: BluetoothAdapter.enable()/disable() require BLUETOOTH_PRIVILEGED
     * on API 33+ and are silently ignored. Use the Android-approved approach:
     * - API 33+: open Bluetooth settings panel.
     * - API 26-32: use BluetoothAdapter.ACTION_REQUEST_ENABLE intent (user-approved).
     * Never report a state change we cannot verify.
     */
    fun openBluetoothSettings(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+: open Bluetooth settings
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Opening Bluetooth settings."
            } else {
                // API 26-32: system-managed enable request dialog
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if (adapter == null) return "Bluetooth is not available on this device."
                if (adapter.isEnabled) {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    "Bluetooth is already on. Opening Bluetooth settings."
                } else {
                    val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    "Requesting Bluetooth enable."
                }
            }
        } catch (e: Exception) {
            ZaraLogger.e("openBluetoothSettings error: ${e.message}")
            "Couldn't open Bluetooth settings."
        }
    }

    fun setFlashlight(on: Boolean): String {
        return try {
            val cam = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cam.cameraIdList.firstOrNull() ?: return "No camera found."
            cam.setTorchMode(id, on)
            if (on) "Flashlight on." else "Flashlight off."
        } catch (e: Exception) {
            ZaraLogger.e("setFlashlight error: ${e.message}")
            "Couldn't control the flashlight."
        }
    }

    fun adjustVolume(direction: String): String {
        val dir = if (direction == "up") AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
        return if (direction == "up") "Volume up." else "Volume down."
    }

    /**
     * C5 fix: setting RINGER_MODE_SILENT requires ACCESS_NOTIFICATION_POLICY
     * on API 23+ or throws SecurityException. Check DND permission first.
     * If not granted, open the DND settings so the user can grant it.
     */
    fun setSilentMode(silent: Boolean): String {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            return try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "I need Do Not Disturb permission to change silent mode. Opening settings."
            } catch (e: Exception) {
                "I need Do Not Disturb permission to change silent mode."
            }
        }
        audio.ringerMode = if (silent) AudioManager.RINGER_MODE_SILENT
                           else AudioManager.RINGER_MODE_NORMAL
        return if (silent) "Silent mode on." else "Ringer on."
    }

    fun lockScreen(): String {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.lockNow()
            "Locking screen."
        } catch (e: Exception) {
            ZaraLogger.e("lockScreen error: ${e.message}")
            "I need device admin permission to lock the screen."
        }
    }
}
