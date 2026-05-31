package com.zara.assistant.permissions

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Centralized permission checks for all Zara features.
 *
 * L3 / C5 fix: added hasDndAccess() for silent mode.
 * ACCESS_NOTIFICATION_POLICY is a special permission granted via settings,
 * not via the runtime permission dialog — it is intentionally excluded from
 * REQUIRED (which covers runtime permissions only).
 */
object PermissionManager {

    /**
     * Runtime permissions requested on first launch.
     * Do NOT include special permissions here (overlay, DND, accessibility,
     * notification listener) — those require dedicated settings intents.
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

    // ── Special permissions (not requestable via runtime dialog) ─────────────

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

    /**
     * C5 / L3: Do Not Disturb access — required to set RINGER_MODE_SILENT.
     * Must be granted by the user via Settings > Apps > Special app access > DND.
     * MediaActions.setSilentMode() checks this before attempting the ringer change.
     */
    fun hasDndAccess(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }
}
