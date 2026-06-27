package com.zara.assistant.automation.core

/**
 * Layer 6.6D Batch 0.2 — AutomationRequest.
 *
 * Generic, app-agnostic description of an automation task to run.
 * Deliberately has no Spotify/YouTube/WhatsApp-specific fields or enums —
 * later Automation Modules interpret `action`/`payload` themselves, so
 * this stays a single reusable shape across all future automations.
 */
data class AutomationRequest(
    val targetApp: String,
    val action: String,
    val payload: Map<String, String> = emptyMap()
)
