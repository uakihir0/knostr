package work.socialhub.knostr

import kotlinx.coroutines.test.runTest
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.relay.RelayPool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A relay only delivers events for the REQ frames sent over its current socket,
 * so the pool has to hand every tracked subscription to a relay each time that
 * socket opens. These tests drive the connection callbacks directly: the
 * connections are never opened, so no REQ can actually go out over the wire.
 */
class RelayPoolResubscribeTest {

    @Test
    fun subscriptionReachesARelayThatConnectsAfterSubscribe() = runTest {
        val pool = RelayPool()
        val sent = pool.recordSentRequests()
        val connection = pool.addRelay("wss://relay.example")

        // Nothing is open yet, so subscribe() has no relay to write to.
        val subId = pool.subscribe(listOf(FILTER), {})
        assertTrue(sent.isEmpty(), "a closed relay cannot receive a REQ")

        pool.bindScope(this)
        connection.onOpenCallback?.invoke()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("wss://relay.example" to subId), sent)
    }

    @Test
    fun subscriptionIsSentAgainWhenTheRelayReconnects() = runTest {
        val pool = RelayPool()
        val sent = pool.recordSentRequests()
        val connection = pool.addRelay("wss://relay.example")
        pool.bindScope(this)

        val subId = pool.subscribe(listOf(FILTER), {})
        connection.onOpenCallback?.invoke()
        connection.onCloseCallback?.invoke()
        connection.onOpenCallback?.invoke()
        testScheduler.advanceUntilIdle()

        // The relay forgot the subscription when its socket died, so the second
        // open has to install it again rather than assume it is still there.
        assertEquals(
            listOf(
                "wss://relay.example" to subId,
                "wss://relay.example" to subId,
            ),
            sent,
        )
    }

    @Test
    fun anUnsubscribedSubscriptionIsNotResent() = runTest {
        val pool = RelayPool()
        val sent = pool.recordSentRequests()
        val connection = pool.addRelay("wss://relay.example")
        pool.bindScope(this)

        val subId = pool.subscribe(listOf(FILTER), {})
        pool.unsubscribe(subId)
        connection.onOpenCallback?.invoke()
        testScheduler.advanceUntilIdle()

        assertTrue(sent.isEmpty(), "the caller is no longer listening")
    }

    @Test
    fun addingTheSameRelayTwiceKeepsOneConnection() {
        val pool = RelayPool()

        val first = pool.addRelay("wss://relay.example")
        val second = pool.addRelay("wss://relay.example")

        // A second connection would open a socket nobody closes, and only one of
        // the two would ever be handed the pool's subscriptions.
        assertSame(first, second)
    }

    @Test
    fun relayStateListenersSeeOpenAndClose() {
        val pool = RelayPool()
        val states = mutableListOf<Pair<String, Boolean>>()
        pool.addRelayStateListener { url, isOpen -> states.add(url to isOpen) }
        val connection = pool.addRelay("wss://relay.example")

        connection.onOpenCallback?.invoke()
        connection.onCloseCallback?.invoke()

        assertEquals(
            listOf(
                "wss://relay.example" to true,
                "wss://relay.example" to false,
            ),
            states,
        )
    }

    @Test
    fun aFaultyRelayStateListenerDoesNotStopTheOthersOrTheResend() = runTest {
        val pool = RelayPool()
        val sent = pool.recordSentRequests()
        val errors = mutableListOf<Pair<String, Exception>>()
        pool.onErrorCallback = { url, e -> errors.add(url to e) }
        pool.addRelayStateListener { _, _ -> throw IllegalStateException("listener failed") }
        val states = mutableListOf<Pair<String, Boolean>>()
        pool.addRelayStateListener { url, isOpen -> states.add(url to isOpen) }
        val connection = pool.addRelay("wss://relay.example")
        pool.bindScope(this)

        val subId = pool.subscribe(listOf(FILTER), {})
        connection.onOpenCallback?.invoke()
        testScheduler.advanceUntilIdle()

        // The exception must not skip the remaining listeners, and the open
        // path still has to install the pool's subscriptions on the socket.
        assertEquals(listOf("wss://relay.example" to true), states)
        assertEquals(1, errors.size)
        assertEquals(listOf("wss://relay.example" to subId), sent)
    }

    @Test
    fun aRemovedRelayStateListenerStopsSeeingState() {
        val pool = RelayPool()
        val states = mutableListOf<Pair<String, Boolean>>()
        val listener: (String, Boolean) -> Unit = { url, isOpen -> states.add(url to isOpen) }
        pool.addRelayStateListener(listener)
        val connection = pool.addRelay("wss://relay.example")

        connection.onOpenCallback?.invoke()
        pool.removeRelayStateListener(listener)
        connection.onCloseCallback?.invoke()

        assertEquals(listOf("wss://relay.example" to true), states)
    }

    /** Replaces the REQ write with a log of (relay url, subscription id). */
    private fun RelayPool.recordSentRequests(): List<Pair<String, String>> {
        val sent = mutableListOf<Pair<String, String>>()
        sendRequest = { connection, subscription ->
            sent.add(connection.url to subscription.id)
        }
        return sent
    }

    private companion object {
        val FILTER = NostrFilter(kinds = listOf(EventKind.TEXT_NOTE))
    }
}
