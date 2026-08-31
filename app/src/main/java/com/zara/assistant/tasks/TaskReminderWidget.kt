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
 * Phase C: the widget now has a Daily/Staged tab bar. The selected tab is
 * stored PER WIDGET ID (see [tabKey]), so multiple widgets can independently
 * show their own section. [TaskWidgetActionReceiver] handles the tab clicks.
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
 * Which section a widget instance is currently showing.
 * Stored per widget id as the enum name — never arbitrary strings.
 */
enum class WidgetTaskTab {
    DAILY,
    STAGED;

    fun toCategory(): TaskCategory = when (this) {
        DAILY  -> TaskCategory.DAILY
        STAGED -> TaskCategory.STAGED
    }

    companion object {
        fun fromName(name: String?): WidgetTaskTab =
            name?.let { runCatching { valueOf(it) }.getOrNull() } ?: DAILY
    }
}

/**
 * Single entry point for refreshing every placed Zara task widget.
 * Called from each task mutation site: ActionExecutor.create (voice),
 * ReminderReceiver fire/done/snooze, and TaskQuickAddActivity completion.
 */
object TaskWidgetSync {

    private const val TAG = "[TaskWidget]"

    /** Row cap per tab. */
    private const val MAX_ROWS = 5

    /** MemoryManager key prefix for the per-widget selected tab. */
    internal const val TAB_KEY_PREFIX = "widget_tab_"

    private const val COLOR_NORMAL = -0x1        // white
    private const val COLOR_OVERDUE = 0xFFFF8A80.toInt()

    /** Fire-and-forget refresh of all widget instances. Never throws. */
    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Phase B: at the start of a new local day, refresh overdue DAILY
                // tasks back to PENDING before rendering. Guarded to run at most
                // once per day, so the frequent updateAll path stays cheap.
                DailyReset.runIfNeeded(MemoryManager(appContext))

                val manager = AppWidgetManager.getInstance(appContext)
                val ids = manager.getAppWidgetIds(
                    ComponentName(appContext, TaskReminderWidget::class.java)
                )
                if (ids.isEmpty()) return@launch

                val memory = MemoryManager(appContext)
                val repo = TaskRepository(memory)
                val active = repo.getActive()

                // Phase C: each widget renders its own selected tab.
                for (id in ids) {
                    val tab = readTab(memory, id)
                    manager.updateAppWidget(id, buildRemoteViews(appContext, active, tab, id))
                }
            } catch (e: Exception) {
                ZaraLogger.e("$TAG update failed: ${e.message}")
            }
        }
    }

    /** Refresh a single widget instance (used after a tab switch). */
    fun updateWidget(context: Context, widgetId: Int) {
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val manager = AppWidgetManager.getInstance(appContext)
                val memory = MemoryManager(appContext)
                val repo = TaskRepository(memory)
                val active = repo.getActive()
                val tab = readTab(memory, widgetId)
                manager.updateAppWidget(widgetId, buildRemoteViews(appContext, active, tab, widgetId))
            } catch (e: Exception) {
                ZaraLogger.e("$TAG updateWidget($widgetId) failed: ${e.message}")
            }
        }
    }

    private fun tabKey(widgetId: Int): String = TAB_KEY_PREFIX + widgetId

    // Compile fix: memory.get() is a suspend function — this must be suspend
    // too. Both call sites (updateAll's per-widget loop, updateWidget) already
    // run inside a launched coroutine, so no caller change was needed.
    private suspend fun readTab(memory: MemoryManager, widgetId: Int): WidgetTaskTab =
        try {
            WidgetTaskTab.fromName(memory.get(tabKey(widgetId)))
        } catch (e: Exception) {
            WidgetTaskTab.DAILY
        }

    internal suspend fun saveTab(memory: MemoryManager, widgetId: Int, tab: WidgetTaskTab) {
        try {
            memory.set(tabKey(widgetId), tab.name)
        } catch (e: Exception) {
            ZaraLogger.e("$TAG saveTab($widgetId) failed: ${e.message}")
        }
    }

    private fun buildRemoteViews(
        context: Context,
        tasks: List<TaskModel>,
        activeTab: WidgetTaskTab,
        widgetId: Int
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_task_reminder)

        rv.setOnClickPendingIntent(R.id.widget_add, quickAddPendingIntent(context))

        // Phase C: style the two tab pills and wire their click intents.
        val daily = activeTab == WidgetTaskTab.DAILY
        val staged = activeTab == WidgetTaskTab.STAGED
        rv.setTextViewText(R.id.widget_tab_daily, "DAILY")
        rv.setTextViewText(R.id.widget_tab_staged, "STAGED")
        styleTab(rv, R.id.widget_tab_daily, daily)
        styleTab(rv, R.id.widget_tab_staged, staged)
        rv.setOnClickPendingIntent(
            R.id.widget_tab_daily,
            tabPendingIntent(context, WidgetTaskTab.DAILY, widgetId)
        )
        rv.setOnClickPendingIntent(
            R.id.widget_tab_staged,
            tabPendingIntent(context, WidgetTaskTab.STAGED, widgetId)
        )

        // Filter by the active tab's category, then sort by trigger time.
        val now = System.currentTimeMillis()
        val visible = tasks
            .filter { it.category() == activeTab.toCategory() }
            .sortedBy { it.effectiveTriggerMs() ?: Long.MAX_VALUE }

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

    private fun styleTab(rv: RemoteViews, viewId: Int, active: Boolean) {
        rv.setInt(
            viewId, "setBackgroundResource",
            if (active) R.drawable.widget_pill_accent else R.drawable.widget_pill_inactive
        )
        if (active) {
            rv.setTextColor(viewId, android.graphics.Color.WHITE)
        } else {
            rv.setTextColor(viewId, 0xFF9E9E9E.toInt())
        }
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

    /**
     * Phase C: builds a broadcast PendingIntent that switches [widgetId]'s tab.
     * The target widget id is baked into the intent (and the request code) so
     * the receiver updates ONLY that widget — no global tab state, and no
     * cross-widget PendingIntent collisions.
     */
    private fun tabPendingIntent(
        context: Context,
        target: WidgetTaskTab,
        widgetId: Int
    ): PendingIntent {
        val intent = Intent(context, TaskWidgetActionReceiver::class.java)
            .setAction(TaskWidgetActionReceiver.ACTION_SWITCH_TAB)
            .putExtra(TaskWidgetActionReceiver.EXTRA_APPWIDGET_ID, widgetId)
            .putExtra(TaskWidgetActionReceiver.EXTRA_TARGET_TAB, target.name)
        return PendingIntent.getBroadcast(
            context,
            50000 + widgetId * 2 + target.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
