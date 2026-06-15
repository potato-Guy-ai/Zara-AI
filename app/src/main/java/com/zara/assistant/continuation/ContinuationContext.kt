package com.zara.assistant.continuation

/**
 * Layer 6.5C — Active continuation state.
 *
 * Tracks whether a continuation-eligible system is currently active
 * and when that state expires. Lazy expiration — checked on access only.
 * No timers. No polling. No background work.
 *
 * TTLs (ms):
 *   CONFIRMATION  → 2 minutes
 *   RECOVERY      → 5 minutes
 *   WORKFLOW      → 5 minutes
 */
enum class ContinuationScope {
    CONFIRMATION,
    RECOVERY,
    WORKFLOW,
    TASK_REGISTRY   // reserved, no execution logic yet
}

data class ContinuationEntry(
    val scope: ContinuationScope,
    val createdAt: Long = System.currentTimeMillis()
) {
    private val ttlMs: Long = when (scope) {
        ContinuationScope.CONFIRMATION  -> 2 * 60 * 1000L
        ContinuationScope.RECOVERY      -> 5 * 60 * 1000L
        ContinuationScope.WORKFLOW      -> 5 * 60 * 1000L
        ContinuationScope.TASK_REGISTRY -> 5 * 60 * 1000L
    }
    fun isExpired(): Boolean = System.currentTimeMillis() - createdAt > ttlMs
}

/**
 * Tracks which continuation scopes are currently active.
 * Updated by VoiceSessionManager when state transitions occur.
 * O(1) lookups.
 */
object ContinuationContext {

    private val active = mutableMapOf<ContinuationScope, ContinuationEntry>()

    fun activate(scope: ContinuationScope) {
        active[scope] = ContinuationEntry(scope)
    }

    fun deactivate(scope: ContinuationScope) {
        active.remove(scope)
    }

    fun isActive(scope: ContinuationScope): Boolean {
        val entry = active[scope] ?: return false
        if (entry.isExpired()) { active.remove(scope); return false }
        return true
    }

    fun clearAll() { active.clear() }

    /** Returns all non-expired active scopes in priority order. */
    fun activeScopes(): List<ContinuationScope> =
        ContinuationScope.entries
            .filter { isActive(it) }
}
