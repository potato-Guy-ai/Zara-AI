package com.zara.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zara.assistant.services.ZaraForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startForegroundService(Intent(context, ZaraForegroundService::class.java))
        }
    }
}
