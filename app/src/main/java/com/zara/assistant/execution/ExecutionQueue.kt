package com.zara.assistant.execution

import com.zara.assistant.core.ZaraIntent
import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.5A — Execution Queue.
 * FIFO, sequential, in-memory.
 * One active task at a time. No threads. No coroutines.
 */
object ExecutionQueue {

    private val queue = ArrayDeque<QueueItem>()

    fun enqueue(item: QueueItem) {
        queue.addLast(item)
        ZaraLogger.d("[Queue] enqueued ${item.plan.id} state=${item.state} queueSize=${queue.size}")
    }

    /** Peek next PENDING item without removing. */
    fun peek(): QueueItem? = queue.firstOrNull { it.state == TaskState.PENDING }

    /** Dequeue and return next PENDING item, transition to RUNNING. */
    fun dequeueNext(): QueueItem? {
        val item = queue.firstOrNull { it.state == TaskState.PENDING } ?: return null
        item.state = TaskState.RUNNING
        return item
    }

    fun markCompleted(id: String) = transition(id, TaskState.COMPLETED)
    fun markFailed(id: String)    = transition(id, TaskState.FAILED)
    fun markCancelled(id: String) = transition(id, TaskState.CANCELLED)
    fun markWaiting(id: String)   = transition(id, TaskState.WAITING)

    private fun transition(id: String, newState: TaskState) {
        queue.firstOrNull { it.plan.id == id }?.let { it.state = newState }
        ZaraLogger.d("[Queue] $id -> $newState")
    }

    fun cancelAll() {
        queue.forEach { if (it.state == TaskState.PENDING) it.state = TaskState.CANCELLED }
    }

    fun clearCompleted() {
        queue.removeAll { it.state == TaskState.COMPLETED || it.state == TaskState.CANCELLED }
    }

    fun size(): Int = queue.size
    fun pendingCount(): Int = queue.count { it.state == TaskState.PENDING }
    fun all(): List<QueueItem> = queue.toList()
}
