package com.zara.assistant.execution

import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.5A + Final Hardening + 6.5B Completion Patch
 *
 * Added: cancelByIds(ids) — cancels only PENDING items whose planId is in the given set.
 * Used by WorkflowEngine.cancelWorkflowItems() for STOP_WORKFLOW cleanup.
 * Does not affect RUNNING, WAITING, COMPLETED, FAILED, or unrelated items.
 */
object ExecutionQueue {

    private val queue = ArrayDeque<QueueItem>()

    fun enqueue(item: QueueItem) {
        queue.addLast(item)
        ZaraLogger.d("[Queue] enqueued ${item.plan.id} state=${item.state} queueSize=${queue.size}")
    }

    fun peek(): QueueItem? = queue.firstOrNull { it.state == TaskState.PENDING }

    fun dequeueNext(): QueueItem? {
        for (item in queue) {
            if (item.state != TaskState.PENDING) continue
            val depId = item.plan.dependsOnId
            if (depId != null) {
                val dep = queue.firstOrNull { it.plan.id == depId }
                when (dep?.state) {
                    TaskState.COMPLETED -> { /* proceed */ }
                    TaskState.FAILED, TaskState.CANCELLED -> {
                        item.state = TaskState.FAILED
                        ZaraLogger.d("[Queue] ${item.plan.id} auto-failed: dependency $depId ${dep.state}")
                        continue
                    }
                    else -> continue
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

    fun markWaitingCompleted(id: String) {
        queue.firstOrNull { it.plan.id == id && it.state == TaskState.WAITING }
            ?.let { it.state = TaskState.COMPLETED }
        ZaraLogger.d("[Queue] $id WAITING -> COMPLETED")
    }

    fun markWaitingCancelled(id: String) {
        queue.firstOrNull { it.plan.id == id && it.state == TaskState.WAITING }
            ?.let { it.state = TaskState.CANCELLED }
        ZaraLogger.d("[Queue] $id WAITING -> CANCELLED")
    }

    /** FIX 2: Cancel only PENDING items whose planId is in the given set. */
    fun cancelByIds(ids: Set<String>) {
        queue.forEach { item ->
            if (item.plan.id in ids && item.state == TaskState.PENDING) {
                item.state = TaskState.CANCELLED
                ZaraLogger.d("[Queue] ${item.plan.id} cancelled by workflow cleanup")
            }
        }
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
