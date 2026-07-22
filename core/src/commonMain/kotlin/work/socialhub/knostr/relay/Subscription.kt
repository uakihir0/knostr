package work.socialhub.knostr.relay

import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Represents an active subscription to relay events.
 */
@OptIn(ExperimentalAtomicApi::class)
data class Subscription(
    val id: String,
    val filters: List<NostrFilter>,
    val onEvent: (NostrEvent) -> Unit,
    /** Invoked when a relay signals end-of-stored-events, with the relay URL. */
    val onEose: ((relayUrl: String) -> Unit)? = null,
) {
    private val seenEventIds = LinkedHashSet<String>()
    private val seenEventIdsLock = AtomicInt(0)

    fun acceptEvent(eventId: String): Boolean = withSeenEventIdsLock {
        if (eventId in seenEventIds) {
            return@withSeenEventIdsLock false
        }
        if (seenEventIds.size >= MAX_SEEN_EVENTS) {
            val iterator = seenEventIds.iterator()
            repeat(MAX_SEEN_EVENTS / 10) {
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
        seenEventIds.add(eventId)
        true
    }

    fun clearSeenEvents() = withSeenEventIdsLock {
        seenEventIds.clear()
    }

    private inline fun <T> withSeenEventIdsLock(block: () -> T): T {
        while (!seenEventIdsLock.compareAndSet(0, 1)) {
            // Relay callbacks only hold this lock for a small in-memory set operation.
        }
        return try {
            block()
        } finally {
            seenEventIdsLock.store(0)
        }
    }

    private companion object {
        const val MAX_SEEN_EVENTS = 10_000
    }
}
