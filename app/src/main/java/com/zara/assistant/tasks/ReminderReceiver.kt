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
 * Phase 4/5 — ReminderReceiver.
 *
 * Three actions:
 *  - [ReminderScheduler.ACTION_TASK_DUE] / [ReminderScheduler.ACTION_TASKS_CATCH_UP]:
 *    the armed alarm firing, or the post-boot/idle catch-up broadcast. Advances
 *    recurring tasks (ReminderScheduler.advanceRecurrence), marks everything
 *    else OVERDUE, then posts one notification/digest via
 *    [ZaraNotificationHelper.notifyDue] for whatever just fired.
 *  - [ACTION_MARK_DONE]: user tapped "Done" on a notification/widget row.
 *  - [ACTION_SNOOZE]: user tapped "Snooze 10 min".
 *
 * Every branch re-arms via ReminderScheduler.scheduleNext() and refreshes the
 * home-screen widget in `finally`, so the alarm chain and widget stay correct
 * regardless of which action ran or whether it threw. goAsync() + SupervisorJob
 * mirrors BootReceiver.kt's pattern for suspend work inside a receiver. Never
 * throws to the caller — all failures logged and swallowed.
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_DONE = "com.zara.assistant.tasks.ACTION_MARK_DONE"
        const val ACTION_SNOOZE = "com.zara.assistant.tasks.ACTION_SNOOZE"
        const val EXTRA_TASK_ID = "task_id"
        private const val SNOOZE_MS = 10L * 60_000
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Phase B: at the start of a new local day, refresh overdue DAILY
                // tasks back to PENDING before handling this action.
                DailyReset.runIfNeeded(MemoryManager(appContext))
                when (intent.action) {
                    ReminderScheduler.ACTION_TASK_DUE,
                    ReminderScheduler.ACTION_TASKS_CATCH_UP -> resolveDueTasks(appContext)
                    ACTION_MARK_DONE -> handleMarkDone(appContext, intent)
                    ACTION_SNOOZE    -> handleSnooze(appContext, intent)
                }
            } catch (e: Exception) {
                ZaraLogger.e("[ReminderReceiver] handling failed: ${e.message}")
            } finally {
                try {
                    ReminderScheduler.scheduleNext(appContext)
                } catch (e: Exception) {
                    ZaraLogger.e("[ReminderReceiver] scheduleNext re-arm failed: ${e.message}")
                }
                TaskWidgetSync.updateAll(appContext)
                result.finish()
            }
        }
    }

    /**
     * Resolves every PENDING task whose effective trigger has already passed:
     * recurring tasks advance to their next occurrence, everything else
     * (including recurrence that can't be advanced) is marked OVERDUE. All
     * tasks resolved this pass are posted as one notification/digest.
     */
    private suspend fun resolveDueTasks(context: Context) {
        val repo = TaskRepository(MemoryManager(context))
        val now = System.currentTimeMillis()
        val duePending = repo.getActive().filter { task ->
            task.state == TaskState.PENDING &&
                (task.effectiveTriggerMs() ?: Long.MAX_VALUE) <= now
        }
        if (duePending.isEmpty()) return

        val fired = mutableListOf<TaskModel>()
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
                    fired.add(task)
                    continue
                }
                ZaraLogger.e("[ReminderReceiver] recurrence exhausted, marking OVERDUE id=${task.id}")
            }
            repo.updateState(task.id, TaskState.OVERDUE)
            fired.add(task)
        }
        ZaraNotificationHelper.notifyDue(context, fired)
    }

    private suspend fun handleMarkDone(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        TaskRepository(MemoryManager(context))
            .updateState(taskId, TaskState.DONE, completedAt = System.currentTimeMillis())
        ZaraNotificationHelper.dismiss(context, taskId)
        ZaraLogger.d("[ReminderReceiver] marked DONE id=$taskId")
    }

    private suspend fun handleSnooze(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        TaskRepository(MemoryManager(context)).snooze(taskId, System.currentTimeMillis() + SNOOZE_MS)
        ZaraNotificationHelper.dismiss(context, taskId)
        ZaraLogger.d("[ReminderReceiver] snoozed id=$taskId")
    }
}
