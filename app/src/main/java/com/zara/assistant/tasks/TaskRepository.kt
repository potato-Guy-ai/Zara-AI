package com.zara.assistant.tasks

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.zara.assistant.memory.MemoryManager
import com.zara.assistant.utils.ZaraLogger

/**
 * Phase 1 — TaskRepository.
 *
 * Operational source of truth for all tasks. Persists to DataStore via
 * MemoryManager under key [KEY_TASKS] as a JSON array. All reads and writes
 * are suspend functions and must be called from a coroutine.
 *
 * Gson serialization strategy for TaskSchedule (sealed class with companion
 * types): each schedule variant is stored as a JsonObject with a "type"
 * discriminator field. No RuntimeTypeAdapterFactory dependency needed because
 * deserialization is done manually in [scheduleFromJson].
 *
 * Phase 6 Obsidian sync: [TaskVaultSync] is called fire-and-forget after each
 * mutating operation. It launches its own IO coroutine and returns before any
 * disk I/O happens, so these calls neither block nor fail the operations above.
 * The vault mirror is best-effort; this class remains the source of truth.
 */
class TaskRepository(private val memory: MemoryManager) {

    companion object {
        private const val KEY_TASKS = "zara_tasks"
        private const val ARCHIVE_MONTHS = 6
        private val gson = Gson()
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    suspend fun getAll(): List<TaskModel> {
        val raw = memory.get(KEY_TASKS) ?: return emptyList()
        return try {
            val arr = JsonParser.parseString(raw).asJsonArray
            arr.mapNotNull { taskFromJson(it.asJsonObject) }
        } catch (e: Exception) {
            ZaraLogger.e("[TaskRepo] getAll parse error: ${e.message}")
            emptyList()
        }
    }

    /** Tasks that need scheduling attention: PENDING or OVERDUE, not cancelled/done. */
    suspend fun getActive(): List<TaskModel> =
        getAll().filter { it.state == TaskState.PENDING || it.state == TaskState.OVERDUE }

    suspend fun getById(id: String): TaskModel? = getAll().find { it.id == id }

    // ── Write ─────────────────────────────────────────────────────────────────

    suspend fun create(task: TaskModel): TaskModel {
        val all = getAll().toMutableList()
        all.add(task)
        save(all)
        ZaraLogger.d("[TaskRepo] created task=${task.id} body=${task.body}")
        TaskVaultSync.upsert(memory, task)
        return task
    }

    suspend fun update(updated: TaskModel) {
        val all = getAll().map { if (it.id == updated.id) updated else it }
        save(all)
        TaskVaultSync.upsert(memory, updated)
    }

    suspend fun updateState(id: String, state: TaskState, completedAt: Long? = null) {
        val task = getById(id) ?: return
        update(task.copy(state = state, completedAt = completedAt))
    }

    /**
     * Phase B — flips DAILY tasks left OVERDUE back to PENDING for a new day.
     * Only category() == DAILY and state == OVERDUE are touched; DONE,
     * CANCELLED, and STAGED tasks are never modified. Operates on the already-
     * active task list once (no redundant full reads).
     *
     * @return the number of tasks reset to PENDING.
     */
    suspend fun resetDailyForNewDay(): Int {
        val due = getActive().filter {
            it.category() == TaskCategory.DAILY && it.state == TaskState.OVERDUE
        }
        due.forEach { updateState(it.id, TaskState.PENDING) }
        return due.size
    }

    suspend fun incrementOverdueCount(id: String) {
        val task = getById(id) ?: return
        update(task.copy(overdueReminderCount = task.overdueReminderCount + 1))
    }

    suspend fun snooze(id: String, until: Long) {
        val task = getById(id) ?: return
        // Re-arm as PENDING with a new schedule so ReminderScheduler picks it up.
        update(task.copy(
            state = TaskState.PENDING,
            schedule = TaskSchedule.Exact(until),
            snoozedUntil = until
        ))
    }

    // ── Archive / cleanup ─────────────────────────────────────────────────────

    /**
     * Removes tasks that are DONE or CANCELLED and older than [ARCHIVE_MONTHS]
     * months, unless tagged "important". Returns the removed tasks so Phase 6
     * can write them to the Obsidian archive.
     *
     * OVERDUE tasks are never auto-archived regardless of age — they are
     * unresolved and must remain visible.
     */
    suspend fun archiveOld(): List<TaskModel> {
        val cutoffMs = System.currentTimeMillis() - ARCHIVE_MONTHS.toLong() * 30 * 24 * 60 * 60 * 1000
        val all = getAll()
        val (toArchive, toKeep) = all.partition { task ->
            (task.state == TaskState.DONE || task.state == TaskState.CANCELLED) &&
            task.createdAt < cutoffMs &&
            !task.isImportant()
        }
        if (toArchive.isNotEmpty()) {
            save(toKeep)
            ZaraLogger.d("[TaskRepo] archived ${toArchive.size} tasks")
            TaskVaultSync.archive(memory, toArchive)
        }
        return toArchive
    }

    // ── Serialization ──────────────────────────────────────────────────────────

    private suspend fun save(tasks: List<TaskModel>) {
        val arr = JsonArray()
        tasks.forEach { arr.add(taskToJson(it)) }
        memory.set(KEY_TASKS, arr.toString())
    }

    private fun taskToJson(task: TaskModel): JsonObject {
        val obj = JsonObject()
        obj.addProperty("id", task.id)
        obj.addProperty("body", task.body)
        obj.addProperty("state", task.state.name)
        obj.add("schedule", scheduleToJson(task.schedule))
        task.deadline?.let { obj.addProperty("deadline", it) }
        task.recurrence?.let {
            val r = JsonObject()
            r.addProperty("type", it.type.name)
            r.addProperty("intervalMs", it.intervalMs)
            obj.add("recurrence", r)
        }
        task.recurrenceAnchorMs?.let { obj.addProperty("recurrenceAnchorMs", it) }
        obj.addProperty("createdAt", task.createdAt)
        task.completedAt?.let { obj.addProperty("completedAt", it) }
        task.snoozedUntil?.let { obj.addProperty("snoozedUntil", it) }
        obj.addProperty("overdueReminderCount", task.overdueReminderCount)
        obj.add("tags", gson.toJsonTree(task.tags))
        return obj
    }

    private fun scheduleToJson(schedule: TaskSchedule): JsonObject {
        val obj = JsonObject()
        obj.addProperty("type", schedule.type)
        when (schedule) {
            is TaskSchedule.Exact -> obj.addProperty("triggerMs", schedule.triggerMs)
            is TaskSchedule.Flexible -> {
                obj.addProperty("windowStart", schedule.windowStart)
                obj.addProperty("windowEnd", schedule.windowEnd)
                obj.addProperty("label", schedule.label)
                obj.addProperty("resolvedTriggerMs", schedule.resolvedTriggerMs)
            }
            TaskSchedule.Unscheduled -> { /* type field is sufficient */ }
        }
        return obj
    }

    private fun taskFromJson(obj: JsonObject): TaskModel? {
        return try {
            TaskModel(
                id       = obj.get("id").asString,
                body     = obj.get("body").asString,
                state    = TaskState.valueOf(obj.get("state").asString),
                schedule = scheduleFromJson(obj.getAsJsonObject("schedule")),
                deadline = obj.get("deadline")?.takeIf { !it.isJsonNull }?.asLong,
                recurrence = obj.get("recurrence")?.takeIf { !it.isJsonNull }?.asJsonObject?.let {
                    RecurrenceRule(
                        type = RecurrenceType.valueOf(it.get("type").asString),
                        intervalMs = it.get("intervalMs").asLong
                    )
                },
                recurrenceAnchorMs = obj.get("recurrenceAnchorMs")?.takeIf { !it.isJsonNull }?.asLong,
                createdAt  = obj.get("createdAt").asLong,
                completedAt = obj.get("completedAt")?.takeIf { !it.isJsonNull }?.asLong,
                snoozedUntil = obj.get("snoozedUntil")?.takeIf { !it.isJsonNull }?.asLong,
                overdueReminderCount = obj.get("overdueReminderCount")?.asInt ?: 0,
                tags = obj.get("tags")?.asJsonArray?.map { it.asString } ?: emptyList()
            )
        } catch (e: Exception) {
            ZaraLogger.e("[TaskRepo] taskFromJson error: ${e.message}")
            null  // skip corrupt entries rather than crashing the whole list
        }
    }

    private fun scheduleFromJson(obj: JsonObject?): TaskSchedule {
        if (obj == null) return TaskSchedule.Unscheduled
        return when (obj.get("type")?.asString) {
            "exact"       -> TaskSchedule.Exact(obj.get("triggerMs").asLong)
            "flexible"    -> TaskSchedule.Flexible(
                windowStart       = obj.get("windowStart").asLong,
                windowEnd         = obj.get("windowEnd").asLong,
                label             = obj.get("label").asString,
                resolvedTriggerMs = obj.get("resolvedTriggerMs")?.asLong
                    ?: obj.get("windowStart").asLong
            )
            "unscheduled" -> TaskSchedule.Unscheduled
            else          -> TaskSchedule.Unscheduled
        }
    }
}
