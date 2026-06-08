package com.zara.assistant.execution

import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.5A + Final Hardening
 *
 * FIX 2: dequeueNext() enforces dependsOnId.
 *   - Dependency COMPLETED   → allow execution
 *   - Dependency FAILED/CANCELLED → auto-fail dependent, skip
 *   - Dependency PENDING/RUNNING/WAITING → skip (not ready)
 *
 * FIX 3: markWaitingCompleted / markWaitingCancelled clear WAITING state.
 */
object ExecutionQueue {

    private val queue = ArrayDeque<QueueItem>()

    fun enqueue(item: QueueItem) {
        queue.addLast(item)
        ZaraLogger.d("[Queue] enqueued ${item.plan.id} state=${item.state} queueSize=${queue.size}")
    }

    fun peek(): QueueItem? = queue.firstOrNull { it.state == TaskState.PENDING }

    /**
     * FIX 2: Returns next executable PENDING item, respecting dependsOnId.
     * Auto-fails items whose dependency has failed/cancelled.
     */
    fun dequeueNext(): QueueItem? {
        for (item in queue) {
            if (item.state != TaskState.PENDING) continue

            val depId = item.plan.dependsOnId
            if (depId != null) {
                val dep = queue.firstOrNull { it.plan.id == depId }
                when (dep?.state) {
                    TaskState.COMPLETED -> { /* dependency satisfied, proceed */ }
                    TaskState.FAILED, TaskState.CANCELLED -> {
                        // Auto-fail dependent
                        item.state = TaskState.FAILED
                        ZaraLogger.d("[Queue] ${item.plan.id} auto-failed: dependency $depId ${dep.state}")
                        continue  // skip to next
                    }
                    else -> continue  // dependency not ready yet
                }
            }

            item.state = TaskState.RUNNING
            return item
        }
        return null
    }

    fun markCompleted(id: String) = transition(id, TaskState.COMPLETED)
    fun markFailed(id: String)    = transition(id, TaskState.FAILED)
    fun markCancelled(id: String) = transition(id, TaskState.CANCELLED)
    fun markWaiting(id: String)   = transition(id, TaskState.WAITING)

    /** FIX 3: Transition WAITING task to COMPLETED after confirmation accepted. */
    fun markWaitingCompleted(id: String) {
        queue.firstOrNull { it.plan.id == id && it.state == TaskState.WAITING }
            ?.let { it.state = TaskState.COMPLETED }
        ZaraLogger.d("[Queue] $id WAITING -> COMPLETED")
    }

    /** FIX 3: Transition WAITING task to CANCELLED after confirmation rejected. */
    fun markWaitingCancelled(id: String) {
        queue.firstOrNull { it.plan.id == id && it.state == TaskState.WAITING }
            ?.let { it.state = TaskState.CANCELLED }
        ZaraLogger.d("[Queue] $id WAITING -> CANCELLED")
    }

    private fun transition(id: String, newState: TaskState) {
        queue.firstOrNull { it.plan.id == id }?.let { it.state = newState }
        ZaraLogger.d("[Queue] $id -> $newState")
    }

    fun cancelAll() {
        queue.forEach { if (it.state == TaskState.PENDING || it.state == TaskState.WAITING) it.state = TaskState.CANCELLED }
    }

    fun clearCompleted() {
        queue.removeAll { it.state == TaskState.COMPLETED || it.state == TaskState.CANCELLED }
    }

    fun getWaiting(): QueueItem? = queue.firstOrNull { it.state == TaskState.WAITING }

    fun size(): Int = queue.size
    fun pendingCount(): Int = queue.count { it.state == TaskState.PENDING }
    fun all(): List<QueueItem> = queue.toList()
}
