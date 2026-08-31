package com.zara.assistant.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase A — derived task category tests.
 *
 * Validates every categorization rule from the expansion plan:
 *   DAILY  <- recurrence DAILY or WEEKDAYS
 *   STAGED <- one-off, WEEKLY, CUSTOM, and any future recurrence type
 *             unless explicitly classified as daily.
 *
 * Pure JUnit (no Android dependencies) — tests only the extension function.
 */
class TaskCategoryTest {

    private fun task(recurrence: RecurrenceRule?) =
        TaskModel(body = "test", recurrence = recurrence)

    @Test
    fun dailyRecurrence_isDaily() {
        assertEquals(TaskCategory.DAILY, task(RecurrenceRule(RecurrenceType.DAILY)).category())
    }

    @Test
    fun weekdayRecurrence_isDaily() {
        assertEquals(TaskCategory.DAILY, task(RecurrenceRule(RecurrenceType.WEEKDAYS)).category())
    }

    @Test
    fun oneOffTask_isStaged() {
        assertEquals(TaskCategory.STAGED, task(null).category())
    }

    @Test
    fun weeklyRecurrence_isStaged() {
        assertEquals(TaskCategory.STAGED, task(RecurrenceRule(RecurrenceType.WEEKLY)).category())
    }

    @Test
    fun customRecurrence_isStaged() {
        assertEquals(
            TaskCategory.STAGED,
            task(RecurrenceRule(RecurrenceType.CUSTOM, intervalMs = 3 * 24 * 60 * 60 * 1000L))
                .category()
        )
    }

    @Test
    fun weeklyTaskOccurringToday_isStaged() {
        // A WEEKLY task whose next trigger is today must still be STAGED —
        // category describes recurrence nature, not whether it fires today.
        val weeklyToday = task(RecurrenceRule(RecurrenceType.WEEKLY))
            .copy(
                schedule = TaskSchedule.Exact(System.currentTimeMillis())
            )
        assertEquals(TaskCategory.STAGED, weeklyToday.category())
    }
}
