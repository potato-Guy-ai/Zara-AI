package com.zara.assistant.tasks

import com.zara.assistant.memory.MemoryManager
import com.zara.assistant.utils.ZaraLogger
import java.time.LocalDate

/**
 * Phase B — once-per-local-calendar-day reset of recurring DAILY tasks.
 *
 * At the start of each new local day, DAILY tasks left in [TaskState.OVERDUE]
 * from the previous day are flipped back to [TaskState.PENDING] so they appear
 * as fresh to-dos again for the new day.
 *
 * Semantics:
 *  - Uses the DEVICE's local calendar day (LocalDate's epoch-day), never UTC
 *    and never a naive 24-hour rollover.
 *  - Only category() == DAILY and state == OVERDUE are reset. DONE, CANCELLED,
 *    and STAGED tasks are never touched.
 *  - Runs at most once per day: the guard [lastDailyResetDay] is persisted to
 *    MemoryManager only AFTER a successful pass, so a failure is retried on the
 *    next eligible call rather than silently skipped.
 *
 * Integration: call [runIfNeeded] from existing lifecycle paths where task
 * state is naturally refreshed (TaskWidgetSync.updateAll, ReminderReceiver,
 * BootReceiver). Do NOT create a separate alarm/service solely for this.
 * Returns true when a reset actually ran (new day processed), false otherwise.
 */
object DailyReset {

    private const val TAG = "[DailyReset]"
    private const val KEY_LAST_DAILY_RESET_DAY = "lastDailyResetDay"

    /**
     * Resets overdue DAILY tasks if a new local calendar day has begun.
     * Never throws to the caller. Fire-and-forget-friendly.
     *
     * @return true if a new-day reset pass was performed (and the guard persisted),
     *         false if it was already done today or the reset failed.
     */
    suspend fun runIfNeeded(memory: MemoryManager): Boolean {
        val currentDay = LocalDate.now().toEpochDay()

        val lastDay = try {
            memory.get(KEY_LAST_DAILY_RESET_DAY)?.toLongOrNull()
        } catch (e: Exception) {
            ZaraLogger.e("$TAG read guard failed: ${e.message}")
            null
        }

        // Same local day (or a corrupt guard) → nothing to do.
        if (lastDay != null && lastDay >= currentDay) return false

        try {
            val repo = TaskRepository(memory)
            val resetCount = repo.resetDailyForNewDay()
            // Persist the guard only after successful processing. Persisting even
            // a 0-count day is correct: it prevents re-scanning every task
            // mutation all day while only DAILY+OVERDUE tasks are reset.
            memory.set(KEY_LAST_DAILY_RESET_DAY, currentDay.toString())
            if (resetCount > 0) {
                ZaraLogger.d("$TAG reset $resetCount overdue DAILY task(s) for new day $currentDay")
            }
            return true
        } catch (e: Exception) {
            // Do NOT advance lastDailyResetDay — retry on the next eligible call.
            ZaraLogger.e("$TAG reset failed, guard not advanced: ${e.message}")
            return false
        }
    }

    /** Clears the guard (used by tests). */
    suspend fun clearGuard(memory: MemoryManager) {
        memory.delete(KEY_LAST_DAILY_RESET_DAY)
    }
}
