package com.zara.assistant.tasks

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.zara.assistant.R
import com.zara.assistant.memory.MemoryManager
import com.zara.assistant.ui.MainActivity
import com.zara.assistant.utils.ZaraLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Phase 5: home-screen task widget.
 *
 * Shows the active/upcoming tasks ([TaskRepository.getActive] — PENDING and
 * OVERDUE) sorted by trigger time, with OVERDUE rows tinted red, plus a
 * voice-first "+ Add" button opening [TaskQuickAddActivity] and a lightweight
 * per-row Done action routed through [ReminderReceiver]'s existing
 * ACTION_MARK_DONE broadcast (same path as notification actions).
 *
 * Deliberately lightweight: plain RemoteViews.addView rows (no ListView /
 * RemoteViewsService), updatePeriodMillis=0, no service / polling / timer.
 * Refresh happens exclusively when task state changes — every mutation site
 * calls [TaskWidgetSync.updateAll].
 */
class TaskReminderWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Async rebuild — repository reads are suspend. The initial layout
        // shows until data lands (a second later at most).
        TaskWidgetSync.updateAll(context)
    }
}

/**
 * Single entry point for refreshing every placed Zara task widget.
 * Called from each task mutation site: ActionExecutor.create (voice),
 * ReminderReceiver fire/done/snooze, and TaskQuickAddActivity completion.
 */
object TaskWidgetSync {

    private const val TAG = "[TaskWidget]"
    private const val MAX_ROWS = 5

    private const val COLOR_NORMAL = -0x1        // white
    private const val COLOR_OVERDUE = 0xFFFF8A80.toInt()

    /** Fire-and-forget refresh of all widget instances. Never throws. */
    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val manager = AppWidgetManager.getInstance(appContext)
                val ids = manager.getAppWidgetIds(
                    ComponentName(appContext, TaskReminderWidget::class.java)
                )
                if (ids.isEmpty()) return@launch

                val repo = TaskRepository(MemoryManager(appContext))
                val active = repo.getActive()
                manager.updateAppWidget(ids, buildRemoteViews(appContext, active))
            } catch (e: Exception) {
                ZaraLogger.e("$TAG update failed: ${e.message}")
            }
        }
    }

    private fun buildRemoteViews(context: Context, tasks: List<TaskModel>): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_task_reminder)

        rv.setOnClickPendingIntent(R.id.widget_add, quickAddPendingIntent(context))

        val now = System.currentTimeMillis()
        val visible = tasks.sortedBy { it.effectiveTriggerMs() ?: Long.MAX_VALUE }

        if (visible.isEmpty()) {
            rv.removeAllViews(R.id.widget_rows)
            rv.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
            rv.setViewVisibility(R.id.widget_more, android.view.View.GONE)
            return rv
        }
        rv.setViewVisibility(R.id.widget_empty, android.view.View.GONE)

        rv.removeAllViews(R.id.widget_rows)
        for (task in visible.take(MAX_ROWS)) {
            rv.addView(R.id.widget_rows, buildRow(context, task, now))
        }

        val extra = visible.size - MAX_ROWS
        if (extra > 0) {
            rv.setTextViewText(R.id.widget_more, "+$extra more")
            rv.setViewVisibility(R.id.widget_more, android.view.View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.widget_more, android.view.View.GONE)
        }
        return rv
    }

    private fun buildRow(context: Context, task: TaskModel, nowMs: Long): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_task_row)

        val trigger = task.effectiveTriggerMs()
        val overdue = task.state == TaskState.OVERDUE || (trigger != null && trigger <= nowMs)

        row.setTextViewText(R.id.widget_row_body, task.body)
        row.setTextColor(
            R.id.widget_row_body,
            if (overdue) COLOR_OVERDUE else COLOR_NORMAL
        )

        // Row body tap → open the app for full management.
        row.setOnClickPendingIntent(R.id.widget_row_root, mainAppPendingIntent(context))

        // Per-task Done → same broadcast the notification action uses.
        val doneIntent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_MARK_DONE)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, task.id)
        val donePi = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        row.setOnClickPendingIntent(R.id.widget_row_done, donePi)
        return row
    }

    private fun quickAddPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            42002,
            Intent(context, TaskQuickAddActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun mainAppPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            42003,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
