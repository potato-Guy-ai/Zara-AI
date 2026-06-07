package com.zara.assistant.core

import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 5 Hardening — Lightweight local telemetry.
 * Session-only. No persistence. No cloud.
 */
object ExecutionTelemetry {

    data class TelemetryRecord(
        val intent: String,
        val resolvedEntity: String?,
        val confidence: String?,
        val selectedApp: String?,
        val selectedContact: String?,
        val executionResult: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val records = mutableListOf<TelemetryRecord>()

    fun record(
        intent: String,
        resolvedEntity: String? = null,
        confidence: String? = null,
        selectedApp: String? = null,
        selectedContact: String? = null,
        executionResult: String
    ) {
        val r = TelemetryRecord(intent, resolvedEntity, confidence, selectedApp, selectedContact, executionResult)
        records.add(r)
        ZaraLogger.d("[Telemetry] intent=$intent entity=$resolvedEntity conf=$confidence app=$selectedApp contact=$selectedContact result=$executionResult")
    }

    fun getRecords(): List<TelemetryRecord> = records.toList()

    fun clear() { records.clear() }
}
