package com.zara.assistant.models

/**
 * Layer 5.4 — Contact alias model.
 * Session-only, in-memory.
 */
data class ContactAlias(
    val alias: String,           // lowercase trimmed alias (e.g. "mame")
    val contactName: String,     // real contact name (e.g. "Abdul Rahman")
    val confidence: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)
