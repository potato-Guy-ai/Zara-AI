package com.zara.assistant.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Centralized permission checks.
 */
object PermissionManager {

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
}
