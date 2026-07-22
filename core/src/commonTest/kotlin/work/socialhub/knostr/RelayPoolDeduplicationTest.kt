package work.socialhub.knostr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.relay.RelayPool
import work.socialhub.knostr.relay.Subscription
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class RelayPoolDeduplicationTest {
    @Test
    fun eventIsDeduplicatedPerSubscription() = runTest {
        val pool = RelayPool()
        val connection = pool.addRelay("wss://relay.example")
        val event = NostrEvent(
            id = "1".repeat(64),
            pubkey = "2".repeat(64),
            createdAt = 1,
            kind = EventKind.TEXT_NOTE,
            tags = listOf(),
            content = "hello",
            sig = "",
        )
        var firstDeliveries = 0
        var secondDeliveries = 0
        val filter = NostrFilter(kinds = listOf(EventKind.TEXT_NOTE))
        val firstSubscription = pool.subscribe(listOf(filter), { firstDeliveries++ })
        val secondSubscription = pool.subscribe(listOf(filter), { secondDeliveries++ })

        connection.onEventCallback?.invoke(firstSubscription, event)
        connection.onEventCallback?.invoke(firstSubscription, event)
        connection.onEventCallback?.invoke(secondSubscription, event)

        assertEquals(1, firstDeliveries)
        assertEquals(1, secondDeliveries)
    }

    @Test
    fun concurrentCopiesAreDeliveredOnlyOnce() = runTest {
        val pool = RelayPool()
        val connections = (1..8).map { pool.addRelay("wss://relay-$it.example") }
        val event = NostrEvent(
            id = "3".repeat(64),
            pubkey = "4".repeat(64),
            createdAt = 1,
            kind = EventKind.TEXT_NOTE,
            tags = listOf(),
            content = "hello",
            sig = "",
        )
        val deliveries = AtomicInt(0)
        val subscription = pool.subscribe(
            listOf(NostrFilter(kinds = listOf(EventKind.TEXT_NOTE))),
            { deliveries.fetchAndAdd(1) },
        )

        withContext(Dispatchers.Default) {
            coroutineScope {
                repeat(100) { index ->
                    launch {
                        connections[index % connections.size].onEventCallback?.invoke(subscription, event)
                    }
                }
            }
        }

        assertEquals(1, deliveries.load())
    }

    @Test
    fun duplicateAtCapacityDoesNotAdvanceEvictionWindow() {
        val subscription = Subscription("id", listOf(), {})
        repeat(10_000) { assertTrue(subscription.acceptEvent(it.toString())) }

        assertFalse(subscription.acceptEvent("9999"))
        assertFalse(subscription.acceptEvent("0"))

        assertTrue(subscription.acceptEvent("new"))
        assertTrue(subscription.acceptEvent("0"))
    }
}
