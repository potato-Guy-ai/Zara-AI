package com.zara.assistant.execution

import com.zara.assistant.core.ZaraIntent

/**
 * Layer 6.5A — Execution Intelligence Core models.
 */

// ── Priority ────────────────────────────────────────────────────────

enum class Priority { HIGH, NORMAL, LOW }

// ── Task State Machine ───────────────────────────────────────────────

enum class TaskState { PENDING, RUNNING, WAITING, COMPLETED, FAILED, CANCELLED }

// ── Execution Requirements / Contracts ───────────────────────────────

enum class ExecutionRequirement {
    CONTACT_REQUIRED,
    APP_REQUIRED,
    INTERNET_REQUIRED,
    PERMISSION_REQUIRED,
    CONFIRMATION_REQUIRED
}

data class ExecutionPlan(
    val id: String,
    val intent: ZaraIntent,
    val requirements: Set<ExecutionRequirement> = emptySet(),
    val priority: Priority = Priority.NORMAL,
    val dependsOnId: String? = null   // ID of task this depends on
)

// ── Queue Item ───────────────────────────────────────────────────────────

data class QueueItem(
    val plan: ExecutionPlan,
    var state: TaskState = TaskState.PENDING,
    val enqueuedAt: Long = System.currentTimeMillis()
)

// ── Failure record ──────────────────────────────────────────────────────

data class FailureRecord(
    val planId: String,
    val intent: ZaraIntent,
    val reason: String,
    val recoveryOptions: List<String>,  // e.g. ["try again", "cancel"]
    val timestamp: Long = System.currentTimeMillis()
)

// ── Confirmation ───────────────────────────────────────────────────────

enum class ConfirmationPolicy { ALWAYS, NEVER, SENSITIVE_ONLY }

data class ConfirmationRequest(
    val planId: String,
    val prompt: String,
    val plan: ExecutionPlan
)

// ── Active task entry ──────────────────────────────────────────────────

data class ActiveTask(
    val type: String,          // "call", "music", "video", "navigation", "app"
    val label: String,
    val packageName: String?,
    val timestamp: Long = System.currentTimeMillis()
)
