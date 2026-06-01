package com.zara.assistant.permissions

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionManager {

    /**
     * Runtime permissions requested on first launch.
     * BLUETOOTH_CONNECT is handled separately (API 31+ only, see hasBluetoothConnect).
     */
    val REQUIRED = arrayOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.CALL_PHONE,
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.SEND_SMS,
        android.Manifest.permission.CAMERA
    )

    fun hasAll(context: Context): Boolean = REQUIRED.all { has(context, it) }

    fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun missing(context: Context): Array<String> =
        REQUIRED.filter { !has(context, it) }.toTypedArray()

    // ── Special permissions ──────────────────────────────────────────────────────

    fun hasOverlay(context: Context): Boolean =
        android.provider.Settings.canDrawOverlays(context)

    fun hasAccessibility(context: Context): Boolean {
        val enabled = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return enabled.contains(context.packageName, ignoreCase = true)
    }

    fun hasNotificationAccess(context: Context): Boolean {
        val enabled = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: ""
        return enabled.contains(context.packageName, ignoreCase = true)
    }

    fun hasDndAccess(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    /**
     * BLUETOOTH_CONNECT is a runtime permission introduced in API 31.
     * Required on API 31-32 for: BluetoothAdapter.isEnabled, ACTION_REQUEST_ENABLE.
     * Not needed on API 26-30 (covered by legacy BLUETOOTH permission).
     * Not needed on API 33+ (ACTION_REQUEST_ENABLE is fully deprecated there).
     *
     * Cannot be added to REQUIRED because it must only be requested on API 31+.
     * MediaActions.openBluetoothSettings() checks this before any Bluetooth call on API 31-32.
     */
    fun hasBluetoothConnect(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true  // not needed below API 31
        return has(context, android.Manifest.permission.BLUETOOTH_CONNECT)
    }
}
