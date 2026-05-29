package com.zara.assistant.actions

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.bluetooth.BluetoothAdapter
import android.app.admin.DevicePolicyManager
import com.zara.assistant.utils.ZaraLogger

class MediaActions(private val context: Context) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun setWifi(on: Boolean): String {
        return try {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled = on
            if (on) "Wi-Fi on" else "Wi-Fi off"
        } catch (e: Exception) { "Couldn't change Wi-Fi state" }
    }

    fun setBluetooth(on: Boolean): String {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (on) adapter?.enable() else adapter?.disable()
            if (on) "Bluetooth on" else "Bluetooth off"
        } catch (e: Exception) { "Couldn't change Bluetooth state" }
    }

    fun setFlashlight(on: Boolean): String {
        return try {
            val cam = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cam.cameraIdList.firstOrNull() ?: return "No camera found"
            cam.setTorchMode(id, on)
            if (on) "Flashlight on" else "Flashlight off"
        } catch (e: Exception) { "Couldn't control flashlight" }
    }

    fun adjustVolume(direction: String): String {
        val dir = if (direction == "up") AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
        return "Volume ${if (direction == "up") "up" else "down"}"
    }

    fun setSilentMode(silent: Boolean): String {
        audio.ringerMode = if (silent) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
        return if (silent) "Silent mode on" else "Ringer on"
    }

    fun lockScreen(): String {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.lockNow()
            "Locking phone"
        } catch (e: Exception) {
            ZaraLogger.e("lockScreen error: ${e.message}")
            "Need device admin permission to lock screen"
        }
    }
}
