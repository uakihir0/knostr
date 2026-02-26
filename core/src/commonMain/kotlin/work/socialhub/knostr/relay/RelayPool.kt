package work.socialhub.knostr.relay

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import kotlin.random.Random

/**
 * Manages multiple relay connections.
 * Handles event deduplication, subscription distribution, and event publishing.
 */
class RelayPool {

    private val connections = mutableMapOf<String, RelayConnection>()
    private val subscriptions = mutableMapOf<String, Subscription>()
    private val seenEventIds = mutableSetOf<String>()
    private val mutex = Mutex()

    /** Callbacks for pool-level events */
    var onEventCallback: ((String, NostrEvent) -> Unit)? = null
    var onOkCallback: ((String, Boolean, String) -> Unit)? = null
    var onNoticeCallback: ((String, String) -> Unit)? = null
    var onErrorCallback: ((String, Exception) -> Unit)? = null

    /** Add a relay connection */
    fun addRelay(url: String): RelayConnection {
        val connection = RelayConnection(url)
        connection.onEventCallback = { subId, event ->
            handleEvent(url, subId, event)
        }
        connection.onOkCallback = { eventId, success, message ->
            onOkCallback?.invoke(eventId, success, message)
        }
        connection.onEoseCallback = { subId ->
            subscriptions[subId]?.onEose?.invoke()
        }
        connection.onNoticeCallback = { message ->
            onNoticeCallback?.invoke(url, message)
        }
        connection.onErrorCallback = { e ->
            onErrorCallback?.invoke(url, e)
        }
        connections[url] = connection
        return connection
    }

    /** Remove a relay connection */
    fun removeRelay(url: String) {
        connections.remove(url)?.close()
    }

    /** Connect to all relays */
    suspend fun connectAll() {
        for (connection in connections.values) {
            if (!connection.isOpen) {
                connection.openAsync()
            }
        }
    }

    /** Disconnect from all relays */
    fun disconnectAll() {
        for (connection in connections.values) {
            connection.close()
        }
    }

    /** Get list of connected relay URLs */
    fun getConnectedRelays(): List<String> {
        return connections.filter { it.value.isOpen }.keys.toList()
    }

    /** Publish an event to all connected relays */
    suspend fun publishEvent(event: NostrEvent) {
        for (connection in connections.values) {
            if (connection.isOpen) {
                connection.sendEvent(event)
            }
        }
    }

    /** Subscribe to events across all connected relays */
    suspend fun subscribe(
        filters: List<NostrFilter>,
        onEvent: (NostrEvent) -> Unit,
        onEose: (() -> Unit)? = null,
    ): String {
        val subId = generateSubscriptionId()
        val subscription = Subscription(subId, filters, onEvent, onEose)
        subscriptions[subId] = subscription

        for (connection in connections.values) {
            if (connection.isOpen) {
                connection.sendReq(subId, filters)
            }
        }
        return subId
    }

    /** Unsubscribe from a subscription */
    suspend fun unsubscribe(subscriptionId: String) {
        subscriptions.remove(subscriptionId)
        for (connection in connections.values) {
            if (connection.isOpen) {
                connection.sendClose(subscriptionId)
            }
        }
    }

    /** Clear seen event IDs cache */
    fun clearSeenEvents() {
        seenEventIds.clear()
    }

    private fun handleEvent(relayUrl: String, subscriptionId: String, event: NostrEvent) {
        // Deduplicate events by ID
        if (!seenEventIds.add(event.id)) {
            return
        }

        // Dispatch to subscription callback
        subscriptions[subscriptionId]?.onEvent?.invoke(event)

        // Dispatch to pool-level callback
        onEventCallback?.invoke(subscriptionId, event)
    }

    private fun generateSubscriptionId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return buildString {
            repeat(16) {
                append(chars[Random.nextInt(chars.length)])
            }
        }
    }
}
