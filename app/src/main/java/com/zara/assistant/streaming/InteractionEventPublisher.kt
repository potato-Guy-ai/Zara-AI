package com.zara.assistant.streaming

/**
 * Layer 6.5E — Interaction Event Publisher.
 *
 * In-memory ring buffer. Bounded at 50 events. No persistence.
 * Observers registered by UI layer; publisher calls them inline.
 * No coroutines, no threads, no frameworks.
 */
object InteractionEventPublisher {

    private const val MAX_HISTORY = 50

    /** Ring buffer of recent events. */
    private val history = ArrayDeque<ZaraInteractionEvent>(MAX_HISTORY)

    /** Registered observers. Populated by UI layer. */
    private val observers = mutableListOf<(ZaraInteractionEvent) -> Unit>()

    /** Register a UI observer. */
    fun observe(observer: (ZaraInteractionEvent) -> Unit) {
        observers.add(observer)
    }

    /** Unregister a UI observer. */
    fun removeObserver(observer: (ZaraInteractionEvent) -> Unit) {
        observers.remove(observer)
    }

    /** Publish an event. Adds to ring buffer and notifies all observers inline. */
    fun publish(event: ZaraInteractionEvent) {
        if (history.size >= MAX_HISTORY) history.removeFirst()
        history.addLast(event)
        observers.forEach { it(event) }
    }

    /** Read-only snapshot of event history. */
    fun history(): List<ZaraInteractionEvent> = history.toList()

    /** Clear history and all observers. */
    fun reset() {
        history.clear()
        observers.clear()
    }
}
