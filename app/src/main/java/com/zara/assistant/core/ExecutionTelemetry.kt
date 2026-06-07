package com.zara.assistant.core

import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 5 Hardening + Final Safety Fixes
 *
 * FIX 4: Bounded circular buffer — MAX_RECORDS = 500.
 * Oldest record dropped when limit exceeded.
 * Session-only. No persistence. No cloud.
 */
object ExecutionTelemetry {

    private const val MAX_RECORDS = 500

    data class TelemetryRecord(
        val intent: String,
        val resolvedEntity: String?,
        val confidence: String?,
        val selectedApp: String?,
        val selectedContact: String?,
        val executionResult: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val records = ArrayDeque<TelemetryRecord>(MAX_RECORDS)

    fun record(
        intent: String,
        resolvedEntity: String?  = null,
        confidence: String?      = null,
        selectedApp: String?     = null,
        selectedContact: String? = null,
        executionResult: String
    ) {
        val r = TelemetryRecord(intent, resolvedEntity, confidence, selectedApp, selectedContact, executionResult)
        if (records.size >= MAX_RECORDS) records.removeFirst()  // drop oldest
        records.addLast(r)
        ZaraLogger.d("[Telemetry] intent=$intent entity=$resolvedEntity conf=$confidence app=$selectedApp contact=$selectedContact result=$executionResult")
    }

    fun getRecords(): List<TelemetryRecord> = records.toList()

    fun clear() { records.clear() }
}
