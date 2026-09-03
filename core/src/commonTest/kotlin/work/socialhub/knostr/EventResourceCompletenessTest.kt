package work.socialhub.knostr

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.internal.EventResourceImpl
import work.socialhub.knostr.relay.RelayPool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class EventResourceCompletenessTest {
    @Test
    fun responseIsCompleteByDefault() {
        assertTrue(Response<List<NostrEvent>>(listOf()).isComplete)
    }

    @Test
    fun queryIsIncompleteWhenEoseTimesOut() = runTest {
        val config = NostrConfig().apply { queryTimeoutMs = 1 }
        val resource = EventResourceImpl(config, RelayPool())

        val response = resource.queryEvents(listOf(NostrFilter(kinds = listOf(EventKind.TEXT_NOTE))))

        assertFalse(response.isComplete)
    }

    @Test
    fun boundedQueryWaitsForTheCallerTimeoutInsteadOfTheConfiguredOne() = runTest {
        val config = NostrConfig().apply { queryTimeoutMs = 60_000 }
        val resource = EventResourceImpl(config, RelayPool())
        val timeoutMs = 10L

        val response = resource.queryEventsWithTimeout(
            filters = listOf(NostrFilter(kinds = listOf(EventKind.TEXT_NOTE))),
            timeoutMs = timeoutMs,
        )

        assertFalse(response.isComplete)
        assertTrue(response.data.isEmpty())
        assertTrue(
            testScheduler.currentTime <= timeoutMs,
            "the query should give up after ${timeoutMs}ms, but waited ${testScheduler.currentTime}ms",
        )
    }

    @Test
    fun boundedQueryKeepsTheEventsThatArrivedBeforeTheTimeout() = runTest {
        val config = NostrConfig().apply { queryTimeoutMs = 60_000 }
        val pool = RelayPool()
        // The relay is never opened, so nothing but the event below reaches the
        // subscription: this is the relay that answers and then goes quiet
        // without ever sending EOSE.
        val connection = pool.addRelay("wss://relay.example.invalid")
        val resource = EventResourceImpl(config, pool)
        val event = NostrEvent(
            id = "a".repeat(64),
            pubkey = "b".repeat(64),
            createdAt = 1,
            kind = EventKind.TEXT_NOTE,
            tags = listOf(),
            content = "note",
            sig = "",
        )

        val query = async {
            resource.queryEventsWithTimeout(
                filters = listOf(NostrFilter(ids = listOf(event.id))),
                timeoutMs = 10,
            )
        }
        val subscriptionId = pool.awaitSubscriptionId()
        connection.onEventCallback?.invoke(subscriptionId, event)

        val response = query.await()

        assertEquals(listOf(event), response.data)
        assertFalse(response.isComplete)
        assertTrue(pool.activeSubscriptionIds().isEmpty(), "the subscription should be dropped")
    }

    /** Waits for the query coroutine to register its subscription. */
    private suspend fun RelayPool.awaitSubscriptionId(): String {
        repeat(SUBSCRIPTION_ATTEMPTS) {
            activeSubscriptionIds().firstOrNull()?.let { return it }
            yield()
        }
        fail("the query did not subscribe")
    }

    private companion object {
        const val SUBSCRIPTION_ATTEMPTS = 100
    }
}
