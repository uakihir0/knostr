package work.socialhub.knostr.relay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.internal.InternalUtility
import work.socialhub.khttpclient.websocket.WebsocketRequest

/**
 * WebSocket connection to a single Nostr relay.
 * Based on kbsky's JetStreamClient pattern using khttpclient.
 */
class RelayConnection(
    val url: String,
    private val autoReconnect: Boolean = false,
    private val maxReconnectAttempts: Int = 5,
    private val reconnectDelayMs: Long = 1_000,
) {
    var client = WebsocketRequest()
    var isOpen: Boolean = false
        private set

    private var reconnectAttempts = 0
    private var reconnectScope: CoroutineScope? = null
    private var intentionallyClosed = false

    // Callbacks
    var onEventCallback: ((String, NostrEvent) -> Unit)? = null
    var onOkCallback: ((String, Boolean, String) -> Unit)? = null
    var onEoseCallback: ((String) -> Unit)? = null
    var onClosedCallback: ((String, String) -> Unit)? = null
    var onNoticeCallback: ((String) -> Unit)? = null
    var onAuthCallback: ((String) -> Unit)? = null
    var onOpenCallback: (() -> Unit)? = null
    var onCloseCallback: (() -> Unit)? = null
    var onErrorCallback: ((Exception) -> Unit)? = null

    init {
        setupClient()
    }

    /** Set the CoroutineScope used for reconnection */
    fun setReconnectScope(scope: CoroutineScope) {
        reconnectScope = scope
    }

    /** Open WebSocket connection (suspending) */
    suspend fun open() {
        intentionallyClosed = false
        client.open()
    }

    /** Close WebSocket connection */
    fun close() {
        intentionallyClosed = true
        client.close()
    }

    /** Send an EVENT message: ["EVENT", event] */
    suspend fun sendEvent(event: NostrEvent) {
        val message = InternalUtility.buildEventMessage(event)
        client.sendText(message)
    }

    /** Send a REQ message: ["REQ", subscription_id, filter1, ...] */
    suspend fun sendReq(subscriptionId: String, filters: List<NostrFilter>) {
        val filterJsons = filters.map { InternalUtility.toJson(it) }
        val message = buildString {
            append("""["REQ","$subscriptionId"""")
            for (filterJson in filterJsons) {
                append(",")
                append(filterJson)
            }
            append("]")
        }
        client.sendText(message)
    }

    /** Send a CLOSE message: ["CLOSE", subscription_id] */
    suspend fun sendClose(subscriptionId: String) {
        val message = InternalUtility.buildCloseMessage(subscriptionId)
        client.sendText(message)
    }

    /** Send an AUTH message: ["AUTH", signedEvent] (NIP-42) */
    suspend fun sendAuth(event: NostrEvent) {
        val message = InternalUtility.buildAuthMessage(event)
        client.sendText(message)
    }

    private fun setupClient() {
        client.url(url)
        client.textListener = { text ->
            onMessage(text)
        }
        client.onOpenListener = {
            isOpen = true
            reconnectAttempts = 0
            onOpenCallback?.invoke()
        }
        client.onCloseListener = {
            isOpen = false
            onCloseCallback?.invoke()
            if (autoReconnect && !intentionallyClosed && reconnectAttempts < maxReconnectAttempts) {
                attemptReconnect()
            }
        }
        client.onErrorListener = { e ->
            onErrorCallback?.invoke(e)
        }
    }

    private fun attemptReconnect() {
        val delayMs = (reconnectDelayMs * (1L shl reconnectAttempts.coerceAtMost(5)))
            .coerceAtMost(30_000)
        reconnectAttempts++
        reconnectScope?.launch {
            delay(delayMs)
            try {
                client = WebsocketRequest()
                setupClient()
                client.open()
            } catch (e: Exception) {
                onErrorCallback?.invoke(e)
            }
        }
    }

    private fun onMessage(text: String) {
        try {
            val message = InternalUtility.parseRelayMessage(text)
            when (message) {
                is RelayMessage.EventMsg -> onEventCallback?.invoke(message.subscriptionId, message.event)
                is RelayMessage.OkMsg -> onOkCallback?.invoke(message.eventId, message.success, message.message)
                is RelayMessage.EoseMsg -> onEoseCallback?.invoke(message.subscriptionId)
                is RelayMessage.ClosedMsg -> onClosedCallback?.invoke(message.subscriptionId, message.message)
                is RelayMessage.NoticeMsg -> onNoticeCallback?.invoke(message.message)
                is RelayMessage.AuthMsg -> onAuthCallback?.invoke(message.challenge)
            }
        } catch (e: Exception) {
            onErrorCallback?.invoke(e)
        }
    }
}
