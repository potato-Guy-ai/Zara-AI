package com.zara.assistant.playback

/**
 * Layer 6.6A Phase 1 — PlaybackResolver.
 *
 * Resolves a PlaybackIntent into a PlaybackTarget.
 * Pure classification. No API calls. No network.
 */
object PlaybackResolver {

    fun resolve(intent: PlaybackIntent): PlaybackTarget {
        val query = intent.target ?: intent.rawQuery
        return PlaybackTarget(
            query = query,
            type = intent.typeHint
        )
    }
}
