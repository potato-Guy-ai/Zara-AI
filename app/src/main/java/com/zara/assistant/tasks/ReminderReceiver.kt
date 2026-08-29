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
 * Single consumer for the one armed reminder alarm ([ReminderScheduler.ACTION_TASK_DUE]),
 * catch-up broadcasts ([ReminderScheduler.ACTION_TASKS_CATCH_UP]), and the
 * notification action buttons ([ACTION_MARK_DONE] / [ACTION_SNOOZE]) built by
 * ZaraNotificationHelper.
 *
 * On fire it processes EVERY PENDING task whose effective trigger has passed:
 *   - recurring → schedule advanced to next occurrence, stays PENDING
 *     ("next instance created only when current is processed" — plan invariant)
 *   - non-recurring → state becomes OVERDUE (awaiting user action)
 * It then posts ONE batch-aware notification (digest when several fired together)
 * and calls ReminderScheduler.scheduleNext() so the chain re-arms.
 *
 * Action buttons:
 *   - Done → completes the CURRENT occurrence only. For recurring tasks the
 *     schedule is re-derived from the pinned anchor and stays PENDING; the
 *     series survives (see ReminderScheduler.advanceRecurrence). Non-recurring
 *     tasks become DONE as before.
 *   - Snooze → TaskRepository.snooze() re-arms as Exact(now + [SNOOZE_MS]).
 *     The recurrence anchor and deadline are untouched by copy(), so the
 *     series continues at its canonical time after the one-off snooze fires.
 * Both dismiss their notification and reschedule via scheduleNext().
 *
 * Threading: TaskRepository is suspend-only (DataStore), which onReceive cannot
 * call directly. goAsync() keeps this receiver alive (~10 s broadcast window)
 * while a coroutine finishes the repo round-trips; finish() always runs.
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        // One shared scope: receivers are short-lived, SupervisorJob isolates failures.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        const val EXTRA_TASK_ID = "task_id"
        const val ACTION_MARK_DONE = "com.zara.assistant.tasks.ACTION_TASK_DONE"
        const val ACTION_SNOOZE = "com.zara.assistant.tasks.ACTION_TASK_SNOOZE"

        const val SNOOZE_MS = 10L * 60 * 1000  // 10 minutes
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext

        // goAsync + coroutine wrapper shared by all branches; finish() always runs.
        fun goAsyncRun(block: suspend () -> Unit) {
            val pendingResult = goAsync()
            scope.launch {
                try {
                    block()
                } catch (e: Exception) {
                    ZaraLogger.e("[ReminderRx] ${intent.action} failed: ${e.message}")
                } finally {
                    pendingResult.finish()
                }
            }
        }

        when (intent.action) {
            ReminderScheduler.ACTION_TASK_DUE,
            ReminderScheduler.ACTION_TASKS_CATCH_UP ->
                goAsyncRun { processDue(appContext) }

            ACTION_MARK_DONE -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                goAsyncRun { completeTask(appContext, taskId) }
            }

            ACTION_SNOOZE -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                goAsyncRun { snoozeTask(appContext, taskId) }
            }
        }
    }

    private suspend fun processDue(context: Context) {
        val now = System.currentTimeMillis()
        val repo = TaskRepository(MemoryManager(context))

        val due = repo.getActive().filter { task ->
            task.state == TaskState.PENDING &&
                (task.effectiveTriggerMs() ?: Long.MAX_VALUE) <= now
        }
        if (due.isEmpty()) return

        for (task in due) {
            val advanced = ReminderScheduler.advanceRecurrence(task, now)
            if (advanced != null) {
                // Recurring: roll forward from the anchor, stay PENDING.
                // state=PENDING is forced (not just copied) so a task that had
                // slipped to OVERDUE can never strand with a future schedule
                // and no armed alarm; snoozedUntil cleared — it just fired.
                repo.update(task.copy(
                    state             = TaskState.PENDING,
                    schedule          = advanced.next,
                    recurrenceAnchorMs = advanced.anchorMs,
                    snoozedUntil      = null
                ))
                ZaraLogger.d("[ReminderRx] recurring fired id=${task.id} → next=${advanced.next.triggerMs}")
            } else {
                repo.updateState(task.id, TaskState.OVERDUE)
                ZaraLogger.d("[ReminderRx] fired id=${task.id} → OVERDUE")
            }
        }

        // One notification per fired batch — several simultaneous tasks collapse
        // into a single digest instead of spamming one per task.
        ZaraNotificationHelper.notifyDue(context, due)

        // Recalculate and arm whatever is next (may legitimately be nothing).
        ReminderScheduler.scheduleNext(context)

        // Phase 5: task states just changed — refresh the widget.
        TaskWidgetSync.updateAll(context)
    }

    private suspend fun completeTask(context: Context, taskId: String) {
        val repo = TaskRepository(MemoryManager(context))
        val task = repo.getById(taskId)

        // Recurring: Done completes THIS occurrence only. Re-derive the next
        // occurrence from the pinned anchor and keep the task alive; only a
        // non-recurring task (or one whose recurrence can no longer produce a
        // future slot) is terminally DONE.
        val advanced = task?.recurrence
            ?.let { ReminderScheduler.advanceRecurrence(task, System.currentTimeMillis()) }
        if (task != null && advanced != null) {
            repo.update(task.copy(
                state             = TaskState.PENDING,
                schedule          = advanced.next,
                recurrenceAnchorMs = advanced.anchorMs,
                snoozedUntil      = null,
                completedAt       = null
            ))
            ZaraLogger.d("[ReminderRx] recurring done id=$taskId → next=${advanced.next.triggerMs}")
        } else {
            repo.updateState(taskId, TaskState.DONE, System.currentTimeMillis())
            ZaraLogger.d("[ReminderRx] done id=$taskId")
        }
        // Action taps don't auto-cancel the notification.
        ZaraNotificationHelper.dismiss(context, taskId)
        ReminderScheduler.scheduleNext(context)
        TaskWidgetSync.updateAll(context)
    }

    private suspend fun snoozeTask(context: Context, taskId: String) {
        val until = System.currentTimeMillis() + SNOOZE_MS
        val repo = TaskRepository(MemoryManager(context))
        repo.snooze(taskId, until)
        ZaraLogger.d("[ReminderRx] snoozed id=$taskId until=$until")
        ZaraNotificationHelper.dismiss(context, taskId)
        ReminderScheduler.scheduleNext(context)
        TaskWidgetSync.updateAll(context)
    }
}
