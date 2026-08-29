package com.zara.assistant.tasks

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zara.assistant.R
import com.zara.assistant.utils.ZaraLogger

/**
 * Phase 4 — ZaraNotificationHelper.
 *
 * Builds and posts task reminder notifications. Purely additive to the
 * scheduling chain: ReminderReceiver calls [notifyDue] once per fired batch;
 * Done/Snooze action buttons send explicit broadcasts back to
 * ReminderReceiver (which owns state mutation and re-arming via
 * ReminderScheduler.scheduleNext).
 *
 * Spam safety: a batch of several simultaneously-due tasks collapses into ONE
 * digest notification instead of one notification per task. Only the
 * single-task notification carries Done/Snooze actions — per-task actions on a
 * digest would be ambiguous.
 *
 * Notification IDs: per-task ID is task.id.hashCode() so re-posts replace and
 * action handlers can dismiss precisely. Action PendingIntents also key their
 * request code off the task id — REQUIRED because PendingIntents created with
 * equal filterEquals would otherwise alias each other's extras.
 *
 * POST_NOTIFICATIONS (API 33+) is declared in the manifest; if the runtime
 * grant is missing, posting is skipped silently (never crashes).
 */
object ZaraNotificationHelper {

    private const val CHANNEL_ID = "zara_reminders"

    // Fixed ID for the collapsed digest. Distinct range from per-task hash ids.
    private const val DIGEST_NOTIFICATION_ID = 42001

    /** Max body lines listed inside a digest's expanded text. */
    private const val DIGEST_MAX_LINES = 4

    // ── Channel ──────────────────────────────────────────────────────────────

    /** Idempotent; safe to call before every post. minSdk 26 → channels always exist. */
    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Task reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders for scheduled tasks"
            }
        )
    }

    // ── Posting ──────────────────────────────────────────────────────────────

    /**
     * Posts ONE notification for a batch of just-fired tasks.
     *
     * [tasks] are the snapshots taken by ReminderReceiver before state updates;
     * only [TaskModel.id] and [TaskModel.body] are read.
     */
    fun notifyDue(context: Context, tasks: List<TaskModel>) {
        if (tasks.isEmpty()) return
        try {
            ensureChannel(context)
            val nm = NotificationManagerCompat.from(context)
            if (!nm.areNotificationsEnabled()) {
                ZaraLogger.d("[Notify] notifications disabled — skipping ${tasks.size} reminder(s)")
                return
            }

            val builder = if (tasks.size == 1) {
                singleTaskBuilder(context, tasks.first())
            } else {
                digestBuilder(context, tasks)
            }

            nm.notify(
                if (tasks.size == 1) tasks.first().id.hashCode() else DIGEST_NOTIFICATION_ID,
                builder.build()
            )
        } catch (e: Exception) {
            // A failed notification must never break the scheduling chain.
            ZaraLogger.e("[Notify] post failed: ${e.message}")
        }
    }

    // ── Dismissal ────────────────────────────────────────────────────────────

    /** Called by ReminderReceiver after Done/Snooze (action taps don't auto-cancel). */
    fun dismiss(context: Context, taskId: String) {
        try {
            NotificationManagerCompat.from(context).cancel(taskId.hashCode())
        } catch (e: Exception) {
            ZaraLogger.e("[Notify] dismiss failed: ${e.message}")
        }
    }

    // ── Builders ─────────────────────────────────────────────────────────────

    private fun singleTaskBuilder(context: Context, task: TaskModel): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Reminder")
            .setContentText(task.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(task.body))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .addAction(0, "Done", actionIntent(context, ReminderReceiver.ACTION_MARK_DONE, task.id))
            .addAction(0, "Snooze 10 min", actionIntent(context, ReminderReceiver.ACTION_SNOOZE, task.id))

    /** Collapsed view when several tasks fire together — no per-task actions. */
    private fun digestBuilder(context: Context, tasks: List<TaskModel>): NotificationCompat.Builder {
        val lines = tasks.take(DIGEST_MAX_LINES).map { "• ${it.body}" }
        val more = tasks.size - lines.size
        val expanded = (lines + if (more > 0) listOf("…and $more more") else emptyList())
            .joinToString("\n")
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("${tasks.size} reminders due")
            .setContentText(tasks.first().body +
                if (tasks.size > 1) " (+${tasks.size - 1} more)" else "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
    }

    // ── PendingIntents ───────────────────────────────────────────────────────

    /** Tap-through: opens the app (singleTask launcher intent). */
    private fun contentIntent(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Explicit broadcast to ReminderReceiver. Request code varies per task id —
     * identical request codes with differing extras would alias extras across
     * concurrently visible notifications.
     */
    private fun actionIntent(context: Context, action: String, taskId: String): PendingIntent {
        val intent = Intent(action)
            .setClassName(context, ReminderReceiver::class.java.name)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context, taskId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
