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
import com.zara.assistant.permissions.PermissionManager
import com.zara.assistant.utils.ZaraLogger

class MediaActions(private val context: Context) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun openWifiSettings(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val intent = Intent(Settings.Panel.ACTION_WIFI)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Opening Wi-Fi settings."
            } else {
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
     * Bluetooth handling by API level:
     *
     * API 33+ (TIRAMISU): ACTION_REQUEST_ENABLE requires BLUETOOTH_PRIVILEGED.
     *   → Open Bluetooth settings directly. No permission needed.
     *
     * API 31-32 (S, S_V2): ACTION_REQUEST_ENABLE and adapter.isEnabled both
     *   require BLUETOOTH_CONNECT runtime permission.
     *   → Check permission first. If missing, guide user to app settings.
     *   → If granted, launch ACTION_REQUEST_ENABLE.
     *
     * API 26-30: BLUETOOTH_CONNECT not required. Use ACTION_REQUEST_ENABLE directly.
     *   adapter.isEnabled is safe without extra permission on these versions.
     */
    fun openBluetoothSettings(): String {
        return try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    // API 33+: settings only, no permission needed
                    context.startActivity(
                        Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    "Opening Bluetooth settings."
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    // API 31-32: BLUETOOTH_CONNECT is a runtime permission required
                    // for both adapter.isEnabled and ACTION_REQUEST_ENABLE
                    if (!PermissionManager.hasBluetoothConnect(context)) {
                        // Guide user to grant it — cannot request from a Service/background context
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                        return "I need Bluetooth permission. Opening app settings."
                    }
                    // Permission granted — safe to check state and request enable
                    val adapter = BluetoothAdapter.getDefaultAdapter()
                        ?: return "Bluetooth is not available on this device."
                    if (adapter.isEnabled) {
                        context.startActivity(
                            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        "Bluetooth is already on. Opening Bluetooth settings."
                    } else {
                        context.startActivity(
                            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        "Requesting Bluetooth enable."
                    }
                }

                else -> {
                    // API 26-30: no BLUETOOTH_CONNECT needed
                    val adapter = BluetoothAdapter.getDefaultAdapter()
                        ?: return "Bluetooth is not available on this device."
                    if (adapter.isEnabled) {
                        context.startActivity(
                            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        "Bluetooth is already on. Opening Bluetooth settings."
                    } else {
                        context.startActivity(
                            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        "Requesting Bluetooth enable."
                    }
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
