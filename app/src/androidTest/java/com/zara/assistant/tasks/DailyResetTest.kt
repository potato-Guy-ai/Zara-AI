package com.zara.assistant.tasks

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zara.assistant.memory.MemoryManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase G — instrumented test for the once-per-local-calendar-day DAILY task
 * reset (Phase B). Runs on device/emulator because MemoryManager is backed by
 * DataStore which needs a real Context.
 *
 * Coverage from the expansion plan:
 *   - DAILY + OVERDUE is flipped to PENDING on a new day
 *   - DONE, CANCELLED and STAGED tasks are never touched
 *   - the same-day guard prevents the reset from running twice
 *   - clearing the guard lets it run again (device-restart-after-midnight path)
 */
@RunWith(AndroidJUnit4::class)
class DailyResetTest {

    private lateinit var memory: MemoryManager

    @Before
    fun setup() {
        memory = MemoryManager(ApplicationProvider.getApplicationContext())
    }

    @After
    fun teardown() = runBlocking {
        memory.clearAll()
    }

    private suspend fun repo() = TaskRepository(memory)

    @Test
    fun dailyOverdue_becomesPending_onNewDay() = runBlocking {
        val overdue = TaskModel(
            body = "daily",
            state = TaskState.OVERDUE,
            recurrence = RecurrenceRule(RecurrenceType.DAILY)
        )
        repo().create(overdue)
        DailyReset.clearGuard(memory)

        assertTrue(DailyReset.runIfNeeded(memory))
        assertTrue(repo().getById(overdue.id)!!.state == TaskState.PENDING)
    }

    @Test
    fun doneCancelledAndStaged_areNeverTouched() = runBlocking {
        val done = TaskModel(
            body = "done", state = TaskState.DONE,
            recurrence = RecurrenceRule(RecurrenceType.DAILY)
        )
        val cancelled = TaskModel(
            body = "cancelled", state = TaskState.CANCELLED,
            recurrence = RecurrenceRule(RecurrenceType.WEEKDAYS)
        )
        val staged = TaskModel(
            body = "staged", state = TaskState.OVERDUE,
            recurrence = RecurrenceRule(RecurrenceType.WEEKLY)
        )
        repo().create(done)
        repo().create(cancelled)
        repo().create(staged)
        DailyReset.clearGuard(memory)

        DailyReset.runIfNeeded(memory)

        assertEquals(TaskState.DONE, repo().getById(done.id)!!.state)
        assertEquals(TaskState.CANCELLED, repo().getById(cancelled.id)!!.state)
        assertEquals(TaskState.OVERDUE, repo().getById(staged.id)!!.state)
    }

    @Test
    fun resetRunsOnlyOncePerDay() = runBlocking {
        val overdue = TaskModel(
            body = "daily",
            state = TaskState.OVERDUE,
            recurrence = RecurrenceRule(RecurrenceType.DAILY)
        )
        repo().create(overdue)
        DailyReset.clearGuard(memory)

        assertTrue(DailyReset.runIfNeeded(memory))
        // Second call on the same local day must be a no-op.
        assertFalse(DailyReset.runIfNeeded(memory))
        assertTrue(repo().getById(overdue.id)!!.state == TaskState.PENDING)
    }

    @Test
    fun clearingGuardLetsResetRunAgain() = runBlocking {
        val overdue = TaskModel(
            body = "daily",
            state = TaskState.OVERDUE,
            recurrence = RecurrenceRule(RecurrenceType.DAILY)
        )
        repo().create(overdue)
        DailyReset.clearGuard(memory)

        DailyReset.runIfNeeded(memory)
        assertEquals(TaskState.PENDING, repo().getById(overdue.id)!!.state)

        // Simulate a new day (restart): mark OVERDUE again and clear the guard.
        repo().updateState(overdue.id, TaskState.OVERDUE)
        DailyReset.clearGuard(memory)

        assertTrue(DailyReset.runIfNeeded(memory))
        assertEquals(TaskState.PENDING, repo().getById(overdue.id)!!.state)
    }
}
