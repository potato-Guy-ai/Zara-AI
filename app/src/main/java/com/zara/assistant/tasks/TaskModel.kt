package com.zara.assistant.tasks

import java.util.UUID

// ── Task state ────────────────────────────────────────────────────────────────

enum class TaskState {
    PENDING,    // created, not yet triggered
    OVERDUE,    // trigger time passed, not completed
    DONE,       // user marked complete
    CANCELLED   // user cancelled or dismissed
}

// ── Schedule types ────────────────────────────────────────────────────────────
// Gson discriminator field: "type" string. No RuntimeTypeAdapterFactory needed
// because we only deserialize via TaskRepository.fromJson() which checks "type"
// explicitly using JsonObject.get("type").asString.

sealed class TaskSchedule {

    abstract val type: String

    /** Exact epoch millisecond trigger. Relative expressions are resolved to this at creation. */
    data class Exact(val triggerMs: Long) : TaskSchedule() {
        override val type = "exact"
    }

    /**
     * Flexible window — e.g. "sometime tomorrow evening".
     * [windowStart]/[windowEnd] are epoch ms. [label] is the canonical name of the
     * window ("tomorrow_evening", "this_weekend", etc.) so user-defined window
     * preferences can override boundaries later without changing the model.
     * [resolvedTriggerMs] is the point Zara selected within the window for the
     * actual alarm. The window is preserved even after resolution.
     */
    data class Flexible(
        val windowStart: Long,
        val windowEnd: Long,
        val label: String,
        val resolvedTriggerMs: Long = windowStart
    ) : TaskSchedule() {
        override val type = "flexible"
    }

    /** No time specified yet. Scheduler does not arm an alarm for unscheduled tasks. */
    object Unscheduled : TaskSchedule() {
        override val type = "unscheduled"
    }
}

// ── Recurrence ────────────────────────────────────────────────────────────────

enum class RecurrenceType {
    DAILY, WEEKLY, WEEKDAYS, CUSTOM
}

/**
 * Defines recurrence for a task.
 * [intervalMs] is used for CUSTOM; ignored for DAILY/WEEKLY/WEEKDAYS (those use
 * calendar-aware logic in ReminderScheduler so DST is handled correctly).
 */
data class RecurrenceRule(
    val type: RecurrenceType,
    val intervalMs: Long = 0L
)

// ── Task model ────────────────────────────────────────────────────────────────

/**
 * Operational task record. TaskRepository is the source of truth.
 *
 * [deadline] is separate from the schedule trigger. "Before 6" populates only
 * this field; the reminder trigger is either explicit (from "at 5") or
 * auto-calculated by the scheduler (deadline - 30 min) for deadline-only tasks.
 *
 * [recurrenceAnchorMs] pins the canonical series time for recurring tasks.
 * It is captured on the first recurrence advance and never modified afterwards —
 * critically, snoozing does NOT touch it, so a snoozed daily 8:00 reminder
 * continues at 8:00 instead of drifting to the snooze time. Null until the
 * first advance; ReminderScheduler falls back to the current trigger then.
 *
 * [tags] used for vault archiving logic: tasks tagged "important" are never
 * auto-archived even when old.
 *
 * Gson serialization note: TaskSchedule is stored as a nested JsonObject with
 * a "type" discriminator. See TaskRepository.toJson / fromJson.
 */
data class TaskModel(
    val id: String = UUID.randomUUID().toString(),
    val body: String,
    val state: TaskState = TaskState.PENDING,
    val schedule: TaskSchedule = TaskSchedule.Unscheduled,
    val deadline: Long? = null,          // epoch ms; null = no deadline
    val recurrence: RecurrenceRule? = null,
    val recurrenceAnchorMs: Long? = null,// epoch ms; canonical recurring series time
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val snoozedUntil: Long? = null,      // epoch ms; null = not snoozed
    val overdueReminderCount: Int = 0,   // how many follow-up overdue nudges sent
    val tags: List<String> = emptyList() // e.g. ["important", "work"]
) {
    /** Epoch ms of the effective alarm trigger for scheduling purposes. */
    fun effectiveTriggerMs(): Long? = when (val s = schedule) {
        is TaskSchedule.Exact    -> s.triggerMs
        is TaskSchedule.Flexible -> s.resolvedTriggerMs
        TaskSchedule.Unscheduled -> deadline?.let { it - DEADLINE_LEAD_MS }
    }

    fun isImportant(): Boolean = "important" in tags

    companion object {
        /** How far before a deadline-only task's deadline to auto-create a reminder. */
        const val DEADLINE_LEAD_MS = 30L * 60 * 1000  // 30 minutes
    }
}
