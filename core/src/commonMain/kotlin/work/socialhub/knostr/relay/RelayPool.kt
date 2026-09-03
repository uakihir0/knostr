package work.socialhub.knostr.relay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import work.socialhub.knostr.EventKind
import work.socialhub.knostr.NostrConfig
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.signing.NostrSigner
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Manages multiple relay connections.
 * Handles event deduplication, subscription distribution, and event publishing.
 */
@OptIn(ExperimentalAtomicApi::class)
class RelayPool {

    private val connections = mutableMapOf<String, RelayConnection>()
    private val subscriptions = AtomicReference<Map<String, Subscription>>(emptyMap())
    private val mutex = Mutex()

    /** Whether any relay is currently connected */
    val isConnected: Boolean
        get() = connections.values.any { it.isOpen }

    /** Signer for auto-auth (NIP-42) */
    var signer: NostrSigner? = null

    /** Whether to automatically respond to AUTH challenges (NIP-42) */
    var autoAuth: Boolean = true

    /** Callbacks for pool-level events */
    var onEventCallback: ((String, NostrEvent) -> Unit)? = null
    var onOkCallback: ((String, Boolean, String) -> Unit)? = null
    var onNoticeCallback: ((String, String) -> Unit)? = null
    var onAuthCallback: ((String, String) -> Unit)? = null
    var onErrorCallback: ((String, Exception) -> Unit)? = null

    /** Add a relay connection */
    fun addRelay(url: String, config: NostrConfig? = null): RelayConnection {
        val connection = RelayConnection(
            url = url,
            autoReconnect = config?.autoReconnect ?: false,
            maxReconnectAttempts = config?.maxReconnectAttempts ?: 5,
            reconnectDelayMs = config?.reconnectDelayMs ?: 1_000,
        )
        connection.onEventCallback = { subId, event ->
            handleEvent(subId, event)
        }
        connection.onOkCallback = { eventId, success, message ->
            onOkCallback?.invoke(eventId, success, message)
        }
        connection.onEoseCallback = { subId ->
            subscriptions.load()[subId]?.onEose?.invoke(url)
        }
        connection.onNoticeCallback = { message ->
            onNoticeCallback?.invoke(url, message)
        }
        connection.onAuthCallback = { challenge ->
            handleAuth(url, challenge, connection)
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

    /** Connect to all relays using the provided CoroutineScope */
    suspend fun connectAll(scope: CoroutineScope) {
        poolScope = scope
        mutex.withLock {
            for (connection in connections.values) {
                connection.setReconnectScope(scope)
                if (!connection.isOpen) {
                    scope.launch {
                        try {
                            connection.open()
                        } catch (e: Exception) {
                            onErrorCallback?.invoke(connection.url, e)
                        }
                    }
                }
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
        mutex.withLock {
            for (connection in connections.values) {
                if (connection.isOpen) {
                    connection.sendEvent(event)
                }
            }
        }
    }

    /** Subscribe to events across all connected relays */
    suspend fun subscribe(
        filters: List<NostrFilter>,
        onEvent: (NostrEvent) -> Unit,
        onEose: ((relayUrl: String) -> Unit)? = null,
    ): String {
        val subId = generateSubscriptionId()
        val subscription = Subscription(subId, filters, onEvent, onEose)
        addSubscription(subscription)
        mutex.withLock {
            for (connection in connections.values) {
                if (connection.isOpen) {
                    connection.sendReq(subId, filters)
                }
            }
        }
        return subId
    }

    /**
     * Unsubscribe from a subscription.
     *
     * The local bookkeeping is dropped before the relays are told, because it
     * needs no relay round trip: a contended mutex or a stalled CLOSE can then
     * no longer keep the callbacks alive.
     */
    suspend fun unsubscribe(subscriptionId: String) {
        removeSubscription(subscriptionId)
        mutex.withLock {
            for (connection in connections.values) {
                if (connection.isOpen) {
                    connection.sendClose(subscriptionId)
                }
            }
        }
    }

    /** Ids of the subscriptions the pool currently tracks. */
    internal fun activeSubscriptionIds(): Set<String> {
        return subscriptions.load().keys
    }

    /** Clear seen event IDs cache */
    fun clearSeenEvents() {
        subscriptions.load().values.forEach { it.clearSeenEvents() }
    }

    private var poolScope: CoroutineScope? = null

    // The subscription map is swapped with compare-and-set so that adding and
    // removing never lose each other's update, even outside the mutex.
    private fun addSubscription(subscription: Subscription) {
        while (true) {
            val current = subscriptions.load()
            val updated = current + (subscription.id to subscription)
            if (subscriptions.compareAndSet(current, updated)) return
        }
    }

    private fun removeSubscription(subscriptionId: String) {
        while (true) {
            val current = subscriptions.load()
            if (subscriptionId !in current) return
            if (subscriptions.compareAndSet(current, current - subscriptionId)) return
        }
    }

    private fun handleAuth(relayUrl: String, challenge: String, connection: RelayConnection) {
        onAuthCallback?.invoke(relayUrl, challenge)

        if (autoAuth) {
            val s = signer ?: return
            poolScope?.launch {
                try {
                    val unsigned = UnsignedEvent(
                        pubkey = s.getPublicKey(),
                        createdAt = Clock.System.now().epochSeconds,
                        kind = EventKind.AUTH,
                        tags = listOf(
                            listOf("relay", relayUrl),
                            listOf("challenge", challenge),
                        ),
                        content = "",
                    )
                    val signed = s.sign(unsigned)
                    connection.sendAuth(signed)
                } catch (e: Exception) {
                    onErrorCallback?.invoke(relayUrl, e)
                }
            }
        }
    }

    private fun handleEvent(subscriptionId: String, event: NostrEvent) {
        val subscription = subscriptions.load()[subscriptionId] ?: return
        if (!subscription.acceptEvent(event.id)) return
        subscription.onEvent.invoke(event)

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
