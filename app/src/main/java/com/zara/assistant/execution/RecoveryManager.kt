package com.zara.assistant.execution

import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.5A — Recovery Manager.
 * Stores last failure, supports retry/resume.
 * Session-only. No persistence.
 */
object RecoveryManager {

    private var lastFailure: FailureRecord? = null

    private val RESUME_WORDS = setOf("continue", "resume", "try again", "retry", "repeat")

    fun recordFailure(record: FailureRecord) {
        lastFailure = record
        ZaraLogger.d("[Recovery] failure recorded: ${record.reason} for ${record.planId}")
    }

    fun hasRecoverable(): Boolean = lastFailure != null

    fun getLastFailure(): FailureRecord? = lastFailure

    fun isResumeCommand(text: String): Boolean {
        val lower = text.trim().lowercase()
        return RESUME_WORDS.any { lower == it || lower.startsWith("$it ") }
    }

    /** Returns and clears the last failure for retry. */
    fun popForRetry(): FailureRecord? {
        val f = lastFailure
        lastFailure = null
        return f
    }

    fun clear() { lastFailure = null }
}
