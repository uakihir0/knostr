package work.socialhub.knostr.relay

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
) {
    var client = WebsocketRequest()
    var isOpen: Boolean = false
        private set

    // Callbacks
    var onEventCallback: ((String, NostrEvent) -> Unit)? = null
    var onOkCallback: ((String, Boolean, String) -> Unit)? = null
    var onEoseCallback: ((String) -> Unit)? = null
    var onClosedCallback: ((String, String) -> Unit)? = null
    var onNoticeCallback: ((String) -> Unit)? = null
    var onOpenCallback: (() -> Unit)? = null
    var onCloseCallback: (() -> Unit)? = null
    var onErrorCallback: ((Exception) -> Unit)? = null

    init {
        client.url(url)
        client.textListener = { text ->
            onMessage(text)
        }
        client.onOpenListener = {
            isOpen = true
            onOpenCallback?.invoke()
        }
        client.onCloseListener = {
            isOpen = false
            onCloseCallback?.invoke()
        }
        client.onErrorListener = { e ->
            onErrorCallback?.invoke(e)
        }
    }

    /** Open WebSocket connection (suspending) */
    suspend fun open() {
        client.open()
    }

    /** Close WebSocket connection */
    fun close() {
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

    private fun onMessage(text: String) {
        try {
            val message = InternalUtility.parseRelayMessage(text)
            when (message) {
                is RelayMessage.EventMsg -> onEventCallback?.invoke(message.subscriptionId, message.event)
                is RelayMessage.OkMsg -> onOkCallback?.invoke(message.eventId, message.success, message.message)
                is RelayMessage.EoseMsg -> onEoseCallback?.invoke(message.subscriptionId)
                is RelayMessage.ClosedMsg -> onClosedCallback?.invoke(message.subscriptionId, message.message)
                is RelayMessage.NoticeMsg -> onNoticeCallback?.invoke(message.message)
            }
        } catch (e: Exception) {
            onErrorCallback?.invoke(e)
        }
    }
}
