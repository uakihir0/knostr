package work.socialhub.knostr.relay

import kotlinx.coroutines.CancellationException
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

    /**
     * Add a relay connection.
     *
     * Adding a url that is already registered returns the existing connection
     * instead of replacing it: a second [RelayConnection] for the same relay
     * would open a duplicate socket that nothing ever closes, and the
     * subscriptions the pool tracks would only reach whichever of the two the
     * map happened to keep. [config] is therefore only honoured for a url the
     * pool does not know yet.
     */
    fun addRelay(url: String, config: NostrConfig? = null): RelayConnection {
        connections[url]?.let { return it }

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
        connection.onClosedCallback = { subId, message ->
            subscriptions.load()[subId]?.onClosed?.invoke(url, message)
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
        connection.onOpenCallback = {
            notifyRelayState(url, true)
            resendSubscriptions(connection)
        }
        connection.onCloseCallback = {
            notifyRelayState(url, false)
        }
        connections[url] = connection
        return connection
    }

    /** Remove a relay connection */
    fun removeRelay(url: String) {
        connections.remove(url)?.close()
    }

    /**
     * Listen for relay sockets opening and closing.
     *
     * Callers that surface a connection state (a UI indicator, a stream
     * lifecycle callback) need this: a single relay dropping is normal, so only
     * the pool knows whether anything is still reachable. Listeners are held as
     * a list because several streams share one pool.
     */
    fun addRelayStateListener(listener: (relayUrl: String, isOpen: Boolean) -> Unit) {
        while (true) {
            val current = relayStateListeners.load()
            if (relayStateListeners.compareAndSet(current, current + listener)) return
        }
    }

    fun removeRelayStateListener(listener: (relayUrl: String, isOpen: Boolean) -> Unit) {
        while (true) {
            val current = relayStateListeners.load()
            if (listener !in current) return
            if (relayStateListeners.compareAndSet(current, current - listener)) return
        }
    }

    /** Connect to all relays using the provided CoroutineScope */
    suspend fun connectAll(scope: CoroutineScope) {
        bindScope(scope)
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

    /**
     * Subscription setup for callers compiled before the reply callbacks were
     * added. Kept so an artifact compiled against an older core keeps linking;
     * new code uses the overload with the full callback set.
     */
    @Deprecated(
        "Use the overload that also reports the request callbacks",
        level = DeprecationLevel.HIDDEN,
    )
    suspend fun subscribe(
        filters: List<NostrFilter>,
        onEvent: (NostrEvent) -> Unit,
        onEose: ((relayUrl: String) -> Unit)? = null,
    ): String = subscribe(filters, onEvent, onEose, null, null, null)

    /**
     * Subscribe to events across all connected relays.
     *
     * Nothing stays registered unless the id is returned: the caller has no way
     * to unsubscribe from a subscription whose id it never learned, so a
     * cancelled or failed setup rolls its own registration back.
     */
    suspend fun subscribe(
        filters: List<NostrFilter>,
        onEvent: (NostrEvent) -> Unit,
        onEose: ((relayUrl: String) -> Unit)? = null,
        onClosed: ((relayUrl: String, message: String) -> Unit)? = null,
        onRequestSending: ((relayUrl: String) -> Unit)? = null,
        onRequestFailed: ((relayUrl: String, error: Exception) -> Unit)? = null,
    ): String {
        val subId = generateSubscriptionId()
        val subscription = Subscription(subId, filters, onEvent, onEose)
        subscription.onClosed = onClosed
        subscription.onRequestSending = onRequestSending
        subscription.onRequestFailed = onRequestFailed
        mutex.withLock {
            addSubscription(subscription)
            try {
                for (connection in connections.values) {
                    if (connection.isOpen) {
                        subscription.onRequestSending?.invoke(connection.url)
                        try {
                            sendRequest(connection, subscription)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // One relay that cannot be written to must not take
                            // the subscription down for the relays that were
                            // reached.
                            subscription.onRequestFailed?.invoke(connection.url, e)
                            onErrorCallback?.invoke(connection.url, e)
                        }
                    }
                }
            } catch (e: Throwable) {
                removeSubscription(subId)
                throw e
            }
        }
        // Relays that are still connecting receive this subscription from
        // resendSubscriptions() once their socket opens.
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
                    try {
                        connection.sendClose(subscriptionId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // The subscription is already dropped locally, so a
                        // CLOSE that cannot be written must not fail the caller.
                        onErrorCallback?.invoke(connection.url, e)
                    }
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

    // Add and remove swap the whole list, so a listener registered while a
    // socket callback is notifying cannot corrupt the snapshot it iterates.
    private val relayStateListeners = AtomicReference<List<(String, Boolean) -> Unit>>(emptyList())

    /**
     * How a subscription is handed to one relay. Overridden by tests, which have
     * no socket to write to.
     */
    internal var sendRequest: suspend (RelayConnection, Subscription) -> Unit =
        { connection, subscription ->
            connection.sendReq(subscription.id, subscription.filters)
        }

    /** Scope used for work that starts from a relay callback. */
    internal fun bindScope(scope: CoroutineScope) {
        poolScope = scope
    }

    /**
     * Send every tracked subscription to a relay whose socket just opened.
     *
     * A REQ only reaches relays that were already connected when [subscribe]
     * ran, and a relay that reconnects starts with no subscriptions at all.
     * Without this the pool reports itself connected while delivering nothing:
     * the relay is waiting for a REQ that was sent to a socket which no longer
     * exists.
     */
    private fun resendSubscriptions(connection: RelayConnection) {
        val current = subscriptions.load().values.toList()
        if (current.isEmpty()) return
        val scope = poolScope ?: return
        scope.launch {
            mutex.withLock {
                for (subscription in current) {
                    // Still tracked? The caller may have unsubscribed while the
                    // socket was opening.
                    if (subscription.id !in subscriptions.load()) continue
                    try {
                        subscription.onRequestSending?.invoke(connection.url)
                        sendRequest(connection, subscription)
                    } catch (e: Exception) {
                        // The relay never received the REQ. Reporting it lets a
                        // query stop waiting on a relay that cannot answer.
                        subscription.onRequestFailed?.invoke(connection.url, e)
                        onErrorCallback?.invoke(connection.url, e)
                    }
                }
            }
        }
    }

    private fun notifyRelayState(relayUrl: String, isOpen: Boolean) {
        for (listener in relayStateListeners.load()) {
            // One faulty observer must not stop the others, and on an open it
            // must not prevent the subscriptions from being resent below: the
            // socket would stay open with nothing listening on it.
            try {
                listener(relayUrl, isOpen)
            } catch (e: Exception) {
                onErrorCallback?.invoke(relayUrl, e)
            }
        }
    }

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
