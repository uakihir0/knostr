package work.socialhub.knostr

import kotlinx.coroutines.test.runTest
import work.socialhub.khttpclient.websocket.WebsocketRequest
import work.socialhub.knostr.relay.RelayConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A reconnect replaces the underlying WebsocketRequest, but the old socket can
 * still dispatch a frame that was already queued when it was replaced. Those
 * frames belong to the socket that is gone and must not be read as replies to
 * the REQ the new socket was sent.
 */
class RelayConnectionTest {

    @Test
    fun aFrameFromTheCurrentSocketIsDelivered() = runTest {
        val connection = RelayConnection("wss://relay.example")
        val eose = mutableListOf<String>()
        connection.onEoseCallback = { eose.add(it) }

        connection.client.textListener("""["EOSE","sub"]""")

        assertEquals(listOf("sub"), eose)
    }

    @Test
    fun aFrameFromAReplacedSocketIsIgnored() = runTest {
        val connection = RelayConnection("wss://relay.example")
        val oldClient = connection.client
        val eose = mutableListOf<String>()
        connection.onEoseCallback = { eose.add(it) }
        // attemptReconnect() installs a new WebsocketRequest and leaves the
        // replaced one's listeners queued on the platform socket.
        connection.client = WebsocketRequest()

        oldClient.textListener("""["EOSE","sub"]""")

        assertTrue(
            eose.isEmpty(),
            "a reply from the socket that was replaced must not count",
        )
    }

    @Test
    fun aCloseFromAReplacedSocketIsIgnored() {
        val connection = RelayConnection("wss://relay.example")
        val oldClient = connection.client
        var closes = 0
        connection.onCloseCallback = { closes++ }
        oldClient.onOpenListener(oldClient)
        assertTrue(connection.isOpen)

        connection.client = WebsocketRequest()
        oldClient.onCloseListener(oldClient)

        assertTrue(connection.isOpen, "the current socket is still open")
        assertEquals(0, closes)
    }
}
