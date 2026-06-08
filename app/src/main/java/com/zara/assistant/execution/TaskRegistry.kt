package com.zara.assistant.execution

import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.5A — Task Registry.
 * Tracks active tasks (call, music, video, navigation, app).
 * Used as foundation for future pause/resume/next commands.
 * Session-only. No persistence.
 */
object TaskRegistry {

    private val active = mutableMapOf<String, ActiveTask>() // type -> task

    fun register(task: ActiveTask) {
        active[task.type] = task
        ZaraLogger.d("[TaskRegistry] registered ${task.type}: ${task.label}")
    }

    fun get(type: String): ActiveTask? = active[type]

    fun getAll(): Map<String, ActiveTask> = active.toMap()

    fun clear(type: String) { active.remove(type) }

    fun clearAll() { active.clear() }
}
