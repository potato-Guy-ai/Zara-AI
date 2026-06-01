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
                context.startActivity(Intent(Settings.Panel.ACTION_WIFI)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            "Opening Wi-Fi settings."
        } catch (e: Exception) {
            ZaraLogger.e("openWifiSettings error: ${e.message}")
            "Couldn't open Wi-Fi settings."
        }
    }

    fun openBluetoothSettings(): String {
        return try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "Opening Bluetooth settings."
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (!PermissionManager.hasBluetoothConnect(context)) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                        return "I need Bluetooth permission. Opening app settings."
                    }
                    val adapter = BluetoothAdapter.getDefaultAdapter()
                        ?: return "Bluetooth is not available on this device."
                    if (adapter.isEnabled) {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        "Bluetooth is already on. Opening Bluetooth settings."
                    } else {
                        context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        "Requesting Bluetooth enable."
                    }
                }
                else -> {
                    val adapter = BluetoothAdapter.getDefaultAdapter()
                        ?: return "Bluetooth is not available on this device."
                    if (adapter.isEnabled) {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        "Bluetooth is already on. Opening Bluetooth settings."
                    } else {
                        context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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

    /**
     * Sound mode control — handles silent, vibrate, and normal/ring.
     *
     * [on]   true  = entering silent or vibrate
     *        false = returning to normal/ring mode
     * [mode] "silent"  → RINGER_MODE_SILENT
     *        "vibrate" → RINGER_MODE_VIBRATE
     *        "normal"  → RINGER_MODE_NORMAL
     */
    fun setSilentMode(on: Boolean, mode: String = "silent"): String {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            return try {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "I need Do Not Disturb permission. Opening settings."
            } catch (e: Exception) {
                "I need Do Not Disturb permission to change sound mode."
            }
        }
        return when {
            !on || mode == "normal" -> {
                audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
                "Ringer on."
            }
            mode == "vibrate" -> {
                audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                "Vibrate mode on."
            }
            else -> {
                audio.ringerMode = AudioManager.RINGER_MODE_SILENT
                "Silent mode on."
            }
        }
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
