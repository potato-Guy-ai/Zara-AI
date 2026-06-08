package com.zara.assistant.execution

import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.5A — Failure Memory.
 * Session-only store of the last failure per action type.
 * No persistence.
 */
object FailureMemory {

    private const val MAX = 20
    private val failures = ArrayDeque<FailureRecord>(MAX)

    fun record(record: FailureRecord) {
        if (failures.size >= MAX) failures.removeFirst()
        failures.addLast(record)
        ZaraLogger.d("[FailureMemory] ${record.planId}: ${record.reason}")
    }

    fun last(): FailureRecord? = failures.lastOrNull()

    fun getAll(): List<FailureRecord> = failures.toList()

    fun clear() { failures.clear() }
}
