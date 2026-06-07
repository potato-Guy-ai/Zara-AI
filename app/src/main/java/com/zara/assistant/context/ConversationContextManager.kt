package com.zara.assistant.context

/**
 * Layer 6.0 — Conversation Context Manager.
 *
 * Single entry point for all session context.
 * Uses ring buffers (ArrayDeque, max 5 per type).
 * No persistence. No background work. No coroutines.
 * Expiry checked on access only.
 */
object ConversationContextManager {

    private const val MAX_HISTORY = 5

    private val persons  = ArrayDeque<PersonContext>(MAX_HISTORY)
    private val apps     = ArrayDeque<AppContext>(MAX_HISTORY)
    private val actions  = ArrayDeque<ActionContext>(MAX_HISTORY)
    private val queries  = ArrayDeque<QueryContext>(MAX_HISTORY)
    private val media    = ArrayDeque<MediaContext>(MAX_HISTORY)

    // ── Update (only after successful execution) ──────────────────────────

    fun updatePerson(name: String, phone: String?, confidence: ContextConfidence = ContextConfidence.HIGH) {
        if (persons.size >= MAX_HISTORY) persons.removeFirst()
        persons.addLast(PersonContext(name, phone, confidence))
    }

    fun updateApp(appName: String, packageName: String?, confidence: ContextConfidence = ContextConfidence.HIGH) {
        if (apps.size >= MAX_HISTORY) apps.removeFirst()
        apps.addLast(AppContext(appName, packageName, confidence))
    }

    fun updateAction(action: String, target: String?, confidence: ContextConfidence = ContextConfidence.HIGH) {
        if (actions.size >= MAX_HISTORY) actions.removeFirst()
        actions.addLast(ActionContext(action, target, confidence))
    }

    fun updateQuery(query: String, queryType: String, confidence: ContextConfidence = ContextConfidence.HIGH) {
        if (queries.size >= MAX_HISTORY) queries.removeFirst()
        queries.addLast(QueryContext(query, queryType, confidence))
    }

    fun updateMedia(song: String?, artist: String?, playlist: String?, video: String?,
                    confidence: ContextConfidence = ContextConfidence.HIGH) {
        if (media.size >= MAX_HISTORY) media.removeFirst()
        media.addLast(MediaContext(song, artist, playlist, video, confidence))
    }

    // ── Retrieve (checks expiry, returns null if expired) ─────────────────

    fun lastPerson(): PersonContext? {
        val p = persons.lastOrNull() ?: return null
        if (p.isExpired()) { persons.removeLast(); return null }
        return p
    }

    fun lastApp(): AppContext? {
        val a = apps.lastOrNull() ?: return null
        if (a.isExpired()) { apps.removeLast(); return null }
        return a
    }

    fun lastAction(): ActionContext? {
        val a = actions.lastOrNull() ?: return null
        if (a.isExpired()) { actions.removeLast(); return null }
        return a
    }

    fun lastQuery(): QueryContext? {
        val q = queries.lastOrNull() ?: return null
        if (q.isExpired()) { queries.removeLast(); return null }
        return q
    }

    fun lastMedia(): MediaContext? {
        val m = media.lastOrNull() ?: return null
        if (m.isExpired()) { media.removeLast(); return null }
        return m
    }

    /** Clear all context (e.g. on session end). */
    fun clearAll() { persons.clear(); apps.clear(); actions.clear(); queries.clear(); media.clear() }
}
