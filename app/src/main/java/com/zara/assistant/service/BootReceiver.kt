package com.zara.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zara.assistant.utils.ZaraLogger

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            ZaraLogger.d("Boot completed — starting Zara")
            ZaraCoreService.start(context)
        }
    }
}
