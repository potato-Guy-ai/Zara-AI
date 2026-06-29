package com.zara.assistant.playback

/**
 * Layer 6.6B Phase 2A — PlaybackExecutionPlan.
 * Immutable orchestration output. No execution logic.
 * cacheKey is the raw normalized query used for PlaybackCache lookups.
 */
data class PlaybackExecutionPlan(
    val route: PlaybackRoute,
    val executionType: PlaybackExecutionType,
    val resolvedQuery: String,
    val targetApp: String?,
    val userTier: UserTier,
    val cacheKey: String
)
