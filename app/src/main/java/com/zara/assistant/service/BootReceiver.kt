package com.zara.assistant.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.zara.assistant.memory.MemoryManager
import com.zara.assistant.services.ZaraForegroundService
import com.zara.assistant.tasks.ReminderScheduler
import com.zara.assistant.tasks.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // The mic-typed FGS cannot be promoted without RECORD_AUDIO
            // (SecurityException on API 34+). Skip if the user never granted it.
            val micGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (micGranted) {
                context.startForegroundService(Intent(context, ZaraForegroundService::class.java))
            }

            // Phase 4 (additive): re-arm the task reminder alarm chain after boot.
            // Reminders are independent of mic permission, so this runs regardless.
            // scheduleNext is suspend (DataStore read) — keep the receiver alive
            // with goAsync() while a coroutine finishes the round-trip.
            //
            // Phase 6 (additive): run the 6-month archive cleanup first. Only
            // DONE/CANCELLED tasks older than the cutoff and not tagged
            // "important" are removed; OVERDUE tasks always stay active. The
            // vault mirror moves those notes to Archive/ fire-and-forget —
            // any failure is contained inside TaskVaultSync.
            val appContext = context.applicationContext
            val result = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    TaskRepository(MemoryManager(appContext)).archiveOld()
                    ReminderScheduler.scheduleNext(appContext)
                } catch (e: Exception) {
                    ZaraLogger.e("[Boot] archiveOld failed: ${e.message}")
                } finally {
                    result.finish()
                }
            }
        }
    }
}
