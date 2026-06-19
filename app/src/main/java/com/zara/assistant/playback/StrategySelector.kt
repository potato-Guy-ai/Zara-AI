package com.zara.assistant.playback

/**
 * Layer 6.6A Phase 1 — StrategySelector.
 *
 * Deterministic rules:
 *   PREMIUM       -> API_MODE
 *   FREE          -> DEEPLINK_MODE
 *   NOT_CONNECTED -> LOCAL_ASSIST_MODE (fallback)
 */
object StrategySelector {

    fun select(tier: UserTier): PlaybackStrategy = when (tier) {
        UserTier.PREMIUM -> PlaybackStrategy.API_MODE
        UserTier.FREE -> PlaybackStrategy.DEEPLINK_MODE
        UserTier.NOT_CONNECTED -> PlaybackStrategy.LOCAL_ASSIST_MODE
    }
}
