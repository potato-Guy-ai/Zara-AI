package com.zara.assistant.playback

/**
 * Layer 6.6B Phase 2A — PlaybackOrchestrator.
 *
 * Accepts a resolved PlaybackTarget + UserTier, determines route,
 * builds and validates a PlaybackExecutionPlan. No execution.
 * No threads. No polling. No timers. No services. No coroutines.
 * O(1) per call.
 */
object PlaybackOrchestrator {

    /**
     * Builds an execution plan for the given resolved target and tier.
     * Returns null if the plan is invalid (never throws).
     */
    fun orchestrate(target: PlaybackTarget, tier: UserTier): PlaybackExecutionPlan? {
        val cacheKey = target.query.trim().lowercase()

        // Cache check — reuse metadata only, no execution.
        val cached = if (cacheKey.isNotBlank()) PlaybackCache.get(cacheKey) else null
        val effectiveTarget = cached ?: target

        val weakResolution = isWeak(effectiveTarget)
        val route = determineRoute(tier, effectiveTarget.type, weakResolution)

        // PerformanceGuard: no device-stress signal exists yet in PerformanceGuard's
        // current contract (MAX_RESULTS/shouldStop are result-count guards, not device
        // stress signals). Route tagging under stress is deferred until that signal exists.

        val executionType = determineExecutionType(effectiveTarget.type, weakResolution)

        val plan = PlaybackExecutionPlan(
            route = route,
            executionType = executionType,
            resolvedQuery = effectiveTarget.query,
            targetApp = null,
            userTier = tier,
            cacheKey = cacheKey
        )

        if (!validate(plan)) return null

        if (cacheKey.isNotBlank() && cached == null) {
            PlaybackCache.put(cacheKey, effectiveTarget)
        }

        return plan
    }

    private fun isWeak(target: PlaybackTarget): Boolean {
        if (target.type == PlaybackType.UNKNOWN) return true
        if (target.type == PlaybackType.LIKED) return false
        return target.query.isBlank()
    }

    private fun determineRoute(tier: UserTier, type: PlaybackType, weak: Boolean): PlaybackRoute {
        if (weak) return PlaybackRoute.FALLBACK_SEARCH
        return when (tier) {
            UserTier.PREMIUM -> PlaybackRoute.PREMIUM_DIRECT
            UserTier.FREE -> PlaybackRoute.FREE_ASSISTED
            UserTier.NOT_CONNECTED -> PlaybackRoute.FALLBACK_SEARCH
        }
    }

    private fun determineExecutionType(type: PlaybackType, weak: Boolean): PlaybackExecutionType {
        if (weak) return PlaybackExecutionType.SEARCH_ONLY
        return when (type) {
            PlaybackType.SONG -> PlaybackExecutionType.PLAY_SONG
            PlaybackType.PLAYLIST -> PlaybackExecutionType.PLAY_PLAYLIST
            PlaybackType.LIKED -> PlaybackExecutionType.PLAY_LIKED
            PlaybackType.RECOMMENDATION -> PlaybackExecutionType.PLAY_RECOMMENDATION
            PlaybackType.ALBUM -> PlaybackExecutionType.PLAY_SONG
            PlaybackType.ARTIST -> PlaybackExecutionType.PLAY_SONG
            PlaybackType.UNKNOWN -> PlaybackExecutionType.SEARCH_ONLY
        }
    }

    /** Rejects invalid plans. Never throws. */
    private fun validate(plan: PlaybackExecutionPlan): Boolean {
        if (plan.executionType != PlaybackExecutionType.PLAY_LIKED &&
            plan.executionType != PlaybackExecutionType.SEARCH_ONLY &&
            plan.resolvedQuery.isBlank()
        ) return false

        if (plan.executionType == PlaybackExecutionType.SEARCH_ONLY &&
            plan.route != PlaybackRoute.FALLBACK_SEARCH
        ) return false

        return true
    }
}
