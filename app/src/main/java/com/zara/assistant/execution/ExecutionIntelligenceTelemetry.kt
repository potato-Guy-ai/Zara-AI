package com.zara.assistant.execution

import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.5A — Execution Telemetry.
 * Lightweight local-only telemetry. No cloud. Bounded buffer.
 */
object ExecutionIntelligenceTelemetry {

    private const val MAX = 200

    data class Record(
        val event: String,
        val planId: String?,
        val detail: String?,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val records = ArrayDeque<Record>(MAX)

    fun track(event: String, planId: String? = null, detail: String? = null) {
        if (records.size >= MAX) records.removeFirst()
        records.addLast(Record(event, planId, detail))
        ZaraLogger.d("[EITelemetry] $event planId=$planId detail=$detail")
    }

    fun getAll(): List<Record> = records.toList()
    fun clear() { records.clear() }
}
