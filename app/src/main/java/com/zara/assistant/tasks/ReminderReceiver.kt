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
 * Phase 4 — ReminderReceiver.
 *
 * Handles both [ReminderScheduler.ACTION_TASK_DUE] (the single armed alarm
 * firing) and [ReminderScheduler.ACTION_TASKS_CATCH_UP] (sent by
 * ReminderScheduler.scheduleNext() when every remaining PENDING trigger has
 * already lapsed, e.g. after the device was off). Both cases funnel through
 * the same resolution logic — see ReminderScheduler's header comment:
 *
 *   1. Re-read active tasks.
 *   2. For every PENDING task whose effective trigger has passed:
 *        - Recurring (task.recurrence != null): advance to the next
 *          occurrence via ReminderScheduler.advanceRecurrence(), persist the
 *          new TaskSchedule.Exact + recurrenceAnchorMs, task stays PENDING.
 *          If advancement fails (recurrence exhausted / cap hit), fall
 *          through to OVERDUE like a non-recurring task.
 *        - Non-recurring: mark OVERDUE. OVERDUE is terminal for scheduling —
 *          the task waits on user action (complete / snooze via a future UI)
 *          and is never re-armed.
 *   3. Call ReminderScheduler.scheduleNext(context) again to arm whatever is
 *      next. This is what lets scheduleNext's own catch-up condition
 *      terminate: each pass here moves a task out of the
 *      "PENDING with lapsed trigger" set (either advanced into the future or
 *      flipped to OVERDUE).
 *
 * goAsync() + SupervisorJob/Dispatchers.IO mirrors BootReceiver.kt's pattern
 * for suspend work inside a receiver. Never crashes the caller — all
 * failures are logged and swallowed, the same defensive style as
 * ReminderScheduler and BootReceiver.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_TASK_DUE &&
            intent.action != ReminderScheduler.ACTION_TASKS_CATCH_UP
        ) return

        val appContext = context.applicationContext
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                resolveDueTasks(appContext)
            } catch (e: Exception) {
                ZaraLogger.e("[ReminderReceiver] resolveDueTasks failed: ${e.message}")
            } finally {
                try {
                    ReminderScheduler.scheduleNext(appContext)
                } catch (e: Exception) {
                    ZaraLogger.e("[ReminderReceiver] scheduleNext re-arm failed: ${e.message}")
                }
                result.finish()
            }
        }
    }

    /**
     * Resolves every PENDING task whose effective trigger has already passed:
     * recurring tasks are advanced to their next occurrence, everything else
     * (including recurrence that can't be advanced) is marked OVERDUE.
     */
    private suspend fun resolveDueTasks(context: Context) {
        val repo = TaskRepository(MemoryManager(context))
        val now = System.currentTimeMillis()
        val duePending = repo.getActive().filter { task ->
            task.state == TaskState.PENDING &&
                (task.effectiveTriggerMs() ?: Long.MAX_VALUE) <= now
        }

        for (task in duePending) {
            if (task.recurrence != null) {
                val advance = ReminderScheduler.advanceRecurrence(task, now)
                if (advance != null) {
                    repo.update(
                        task.copy(
                            schedule = advance.next,
                            recurrenceAnchorMs = advance.anchorMs
                        )
                    )
                    ZaraLogger.d("[ReminderReceiver] advanced recurrence id=${task.id} next=${advance.next.triggerMs}")
                    continue
                }
                ZaraLogger.e("[ReminderReceiver] recurrence exhausted, marking OVERDUE id=${task.id}")
            }
            repo.updateState(task.id, TaskState.OVERDUE)
            ZaraLogger.d("[ReminderReceiver] marked OVERDUE id=${task.id}")
        }
    }
}
