package work.socialhub.knostr

import kotlinx.coroutines.test.runTest
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.relay.RelayPool
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
