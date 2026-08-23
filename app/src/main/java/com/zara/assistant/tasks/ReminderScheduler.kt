package com.zara.assistant.tasks

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.zara.assistant.memory.MemoryManager
import com.zara.assistant.utils.ZaraLogger
import java.util.Calendar

/**
 * Phase 4 — ReminderScheduler.
 *
 * Zara owns scheduling. Exactly ONE AlarmManager alarm is armed at any time:
 * the next relevant task event. After the receiver processes a fired event,
 * it calls [scheduleNext] again to arm whatever is next — no alarm per task,
 * no Clock-app intents, no continuously running service or timer.
 *
 * Event model (see TaskModel.effectiveTriggerMs):
 *   Exact    → triggerMs
 *   Flexible → resolvedTriggerMs (window preserved in storage, never collapsed)
 *   Unscheduled + deadline → deadline - DEADLINE_LEAD_MS  (auto-reminder so a
 *                            deadline-only task is not invisible until overdue)
 *
 * Missed events: if every remaining PENDING trigger already passed (device was
 * off / rebooted), scheduleNext sends an explicit catch-up broadcast to
 * ReminderReceiver instead of arming an alarm in the past. The receiver marks
 * those tasks OVERDUE (or advances recurrence) and reschedules — one code path
 * for firing and catching up. The loop terminates because every pass moves a
 * task out of the "PENDING with lapsed trigger" set.
 *
 * OVERDUE tasks are terminal for scheduling: their event already fired and we
 * are waiting on user action (complete / snooze via a future UI). They are
 * never re-armed.
 *
 * Exact-alarm resilience: SCHEDULE_EXACT_ALARM is checked before use
 * (AlarmManager.canScheduleExactAlarms() on API 31+, same defensive pattern as
 * PermissionManager.hasBluetoothConnect). If unavailable → setAndAllowWhileIdle.
 * The scheduler never crashes on a missing permission or any other failure —
 * all errors are logged and swallowed; a missed alarm degrades gracefully.
 */
object ReminderScheduler {

    /** Fired by AlarmManager when the single armed reminder alarm goes off. */
    const val ACTION_TASK_DUE = "com.zara.assistant.tasks.ACTION_TASK_DUE"

    /**
     * Sent by [scheduleNext] when PENDING tasks have lapsed triggers but there
     * is nothing future to arm (e.g. right after boot). ReminderReceiver treats
     * this identically to ACTION_TASK_DUE.
     */
    const val ACTION_TASKS_CATCH_UP = "com.zara.assistant.tasks.ACTION_TASKS_CATCH_UP"

    // Fixed request code → every new PendingIntent REPLACES the previous one,
    // enforcing the one-alarm-at-a-time invariant at the AlarmManager level.
    private const val REQUEST_CODE = 4001

    /**
     * Reads active tasks and arms the next relevant event (or cancels the
     * pending alarm / sends catch-up when nothing remains).
     *
     * Suspending because TaskRepository is DataStore-backed; callers:
     *   - ActionExecutor (already suspend) calls directly.
     *   - Receivers wrap in goAsync + coroutine scope.
     * Fire-and-forget from the caller's perspective — never throws.
     */
    suspend fun scheduleNext(context: Context) {
        val appContext = context.applicationContext
        try {
            val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (am == null) {
                ZaraLogger.e("[ReminderScheduler] AlarmManager unavailable")
                return
            }

            val now = System.currentTimeMillis()
            val repo = TaskRepository(MemoryManager(appContext))
            val pendingTasks = repo.getActive().filter { it.state == TaskState.PENDING }

            // Next future trigger across all pending tasks…
            val nextFuture = pendingTasks
                .mapNotNull { it.effectiveTriggerMs()?.takeIf { ms -> ms > now } }
                .minOrNull()

            if (nextFuture != null) {
                arm(appContext, am, nextFuture)
                return
            }

            // Nothing future. Cancel any stale alarm first…
            am.cancel(pendingIntent(appContext))

            // …then check whether events lapsed entirely (device off / reboot).
            // Funnel them through the receiver instead of mutating state here —
            // state transitions live in exactly one place.
            val hasLapsed = pendingTasks.any {
                (it.effectiveTriggerMs() ?: Long.MAX_VALUE) <= now
            }
            if (hasLapsed) {
                appContext.sendBroadcast(
                    Intent(ACTION_TASKS_CATCH_UP).setClassName(appContext, ReminderReceiver::class.java.name)
                )
                ZaraLogger.d("[ReminderScheduler] catch-up broadcast sent")
            } else {
                ZaraLogger.d("[ReminderScheduler] nothing to schedule — idle")
            }
        } catch (e: Exception) {
            // Never crash the caller (voice path / boot receiver) over scheduling.
            ZaraLogger.e("[ReminderScheduler] scheduleNext failed: ${e.message}")
        }
    }

    /** Cancels the currently armed reminder alarm, if any. Lightweight no-op otherwise. */
    fun cancel(context: Context) {
        try {
            val am = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            am.cancel(pendingIntent(context.applicationContext))
        } catch (e: Exception) {
            ZaraLogger.e("[ReminderScheduler] cancel failed: ${e.message}")
        }
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private fun arm(context: Context, am: AlarmManager, triggerMs: Long) {
        val pi = pendingIntent(context)
        am.cancel(pi) // explicit replace; also corrects any drift from prior builds
        if (canScheduleExact(am)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        }
        ZaraLogger.d("[ReminderScheduler] armed next event at=$triggerMs exact=${canScheduleExact(am)}")
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(ACTION_TASK_DUE).setClassName(context, ReminderReceiver::class.java.name)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Below API 31 set-exact needs no permission. On API 31+ SCHEDULE_EXACT_ALARM
     * may be revoked by the user/OEM — fall back to inexact windowless alarms.
     */
    private fun canScheduleExact(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    // ── Recurrence advancement (used by ReminderReceiver) ─────────────────────

    /**
     * Safety cap while rolling a recurrence forward past lapsed time
     * (e.g. device off for weeks). Prevents pathological loops on tiny
     * CUSTOM intervals.
     */
    private const val MAX_RECURRENCE_CATCHUP = 730  // ~2 years of daily steps

    /** CUSTOM intervals shorter than this are clamped to avoid hot loops. */
    private const val MIN_CUSTOM_INTERVAL_MS = 60_000L

    /**
     * Result of advancing a recurring task one occurrence forward.
     * [anchorMs] must be persisted into [TaskModel.recurrenceAnchorMs] by the
     * caller together with [next] — it pins the canonical series time forever,
     * so later snoozes or missed fires can never shift the wall-clock time.
     */
    data class RecurrenceAdvance(val next: TaskSchedule.Exact, val anchorMs: Long)

    /**
     * Computes the next occurrence of [task] after [nowMs].
     *
     * Rolling always starts from the recurrence ANCHOR — the stored
     * [TaskModel.recurrenceAnchorMs], or (first advance only) the trigger of
     * the occurrence being acknowledged. This is what makes the series stable:
     *
     *   - Snooze inserts a one-off Exact(until) between occurrences; when THAT
     *     fires, advancement re-derives from the anchor, not the snooze instant,
     *     so a daily 8:00 reminder stays at 8:00 instead of drifting.
     *   - Catch-up after days offline converges straight to the next valid slot.
     *   - Acknowledging an occurrence early (Done before it fires) pins the
     *     anchor to that pending trigger and skips exactly one occurrence.
     *
     * Calendar-aware per step so DST transitions preserve local time-of-day.
     *
     * Returns null when the task does not recur, has no derivable trigger, or
     * cannot reach a future instant within [MAX_RECURRENCE_CATCHUP] steps (the
     * caller treats that as terminal — e.g. marks it DONE / leaves it OVERDUE).
     */
    fun advanceRecurrence(task: TaskModel, nowMs: Long): RecurrenceAdvance? {
        val rule = task.recurrence ?: return null
        val anchor = task.recurrenceAnchorMs ?: task.effectiveTriggerMs() ?: return null
        var t = anchor
        var steps = 0
        do {
            t = advanceOnce(t, rule)
            if (++steps > MAX_RECURRENCE_CATCHUP) {
                ZaraLogger.e("[ReminderScheduler] recurrence cap hit id=${task.id}")
                return null
            }
        } while (t <= nowMs)
        return RecurrenceAdvance(TaskSchedule.Exact(t), anchor)
    }

    private fun advanceOnce(triggerMs: Long, rule: RecurrenceRule): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = triggerMs }
        when (rule.type) {
            RecurrenceType.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            RecurrenceType.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            RecurrenceType.WEEKDAYS -> do {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            } while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                     cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
            RecurrenceType.CUSTOM ->
                cal.timeInMillis += rule.intervalMs.coerceAtLeast(MIN_CUSTOM_INTERVAL_MS)
        }
        return cal.timeInMillis
    }
}
