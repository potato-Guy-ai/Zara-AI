package com.zara.assistant.tasks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zara.assistant.memory.MemoryManager
import com.zara.assistant.utils.ZaraLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Phase C — handles user interaction with the Zara task widget, specifically
 * the Daily/Staged tab buttons.
 *
 * Kept OUT of [ReminderReceiver] on purpose: ReminderReceiver owns reminder
 * scheduling/alarms; widget-UI actions belong here. Each tab PendingIntent
 * carries [EXTRA_APPWIDGET_ID] + [EXTRA_TARGET_TAB], so this receiver updates
 * ONLY that widget's tab state — there is no global tab state.
 *
 * Mirrors the other receivers' goAsync() + SupervisorJob pattern for the short
 * suspend round-trip (DataStore write) and never throws to the caller.
 */
class TaskWidgetActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SWITCH_TAB = "com.zara.assistant.tasks.ACTION_SWITCH_TAB"
        const val EXTRA_APPWIDGET_ID = "appwidget_id"
        const val EXTRA_TARGET_TAB = "target_tab"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SWITCH_TAB) return
        val widgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, -1)
        if (widgetId < 0) return
        val target = WidgetTaskTab.fromName(intent.getStringExtra(EXTRA_TARGET_TAB))

        val appContext = context.applicationContext
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                TaskWidgetSync.saveTab(MemoryManager(appContext), widgetId, target)
                TaskWidgetSync.updateWidget(appContext, widgetId)
            } catch (e: Exception) {
                ZaraLogger.e("[TaskWidgetAction] tab switch failed: ${e.message}")
            } finally {
                result.finish()
            }
        }
    }
}
