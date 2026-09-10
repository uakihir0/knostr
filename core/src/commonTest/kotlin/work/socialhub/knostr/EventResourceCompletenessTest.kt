package work.socialhub.knostr

import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun queryIsCompleteWhenEveryReceivingRelayReplies() = runTest {
        val config = NostrConfig().apply { queryTimeoutMs = 60_000 }
        val pool = RelayPool()
        pool.sendRequest = { _, _ -> }
        pool.bindScope(this)
        val first = pool.addRelay("wss://first.example")
        val second = pool.addRelay("wss://second.example")
        val resource = EventResourceImpl(config, pool)

        val query = async {
            resource.queryEventsWithTimeout(
                filters = listOf(NostrFilter(kinds = listOf(EventKind.TEXT_NOTE))),
                timeoutMs = 30_000,
            )
        }
        val subscriptionId = pool.awaitSubscriptionId()
        // Both sockets open while the query waits, so the pool hands each of
        // them the subscription.
        first.onOpenCallback?.invoke()
        second.onOpenCallback?.invoke()
        testScheduler.runCurrent()
        first.onEoseCallback?.invoke(subscriptionId)
        yield()
        assertFalse(query.isCompleted, "the second relay has not replied yet")
        second.onEoseCallback?.invoke(subscriptionId)

        val response = query.await()

        assertTrue(response.isComplete)
        assertTrue(
            testScheduler.currentTime < 30_000,
            "the query should not wait out its timeout, but waited ${testScheduler.currentTime}ms",
        )
    }

    @Test
    fun aRelayThatReceivesTheSubscriptionMidQueryMustAlsoReply() = runTest {
        val config = NostrConfig().apply { queryTimeoutMs = 60_000 }
        val pool = RelayPool()
        pool.sendRequest = { _, _ -> }
        pool.bindScope(this)
        val open = pool.addRelay("wss://open.example")
        val connecting = pool.addRelay("wss://connecting.example")
        val resource = EventResourceImpl(config, pool)

        val query = async {
            resource.queryEventsWithTimeout(
                filters = listOf(NostrFilter(kinds = listOf(EventKind.TEXT_NOTE))),
                timeoutMs = 30_000,
            )
        }
        val subscriptionId = pool.awaitSubscriptionId()
        // The connecting relay opens first and the pool hands it the
        // subscription, so it is a participant now.
        connecting.onOpenCallback?.invoke()
        testScheduler.runCurrent()
        open.onOpenCallback?.invoke()
        testScheduler.runCurrent()
        open.onEoseCallback?.invoke(subscriptionId)
        yield()
        assertFalse(
            query.isCompleted,
            "the relay that just received the subscription has not replied",
        )

        connecting.onEoseCallback?.invoke(subscriptionId)

        assertTrue(query.await().isComplete)
    }

    @Test
    fun aRelayThatOpensMidQueryCannotStandInForASilentOne() = runTest {
        val config = NostrConfig().apply { queryTimeoutMs = 60_000 }
        val pool = RelayPool()
        pool.sendRequest = { _, _ -> }
        pool.bindScope(this)
        val first = pool.addRelay("wss://first.example")
        val second = pool.addRelay("wss://second.example")
        val resource = EventResourceImpl(config, pool)

        val query = async {
            resource.queryEventsWithTimeout(
                filters = listOf(NostrFilter(kinds = listOf(EventKind.TEXT_NOTE))),
                timeoutMs = 5_000,
            )
        }
        val subscriptionId = pool.awaitSubscriptionId()
        first.onOpenCallback?.invoke()
        second.onOpenCallback?.invoke()
        testScheduler.runCurrent()
        val late = pool.addRelay("wss://late.example")
        late.onOpenCallback?.invoke()
        testScheduler.runCurrent()

        // Two relays answered, but the second relay the query started with is
        // still silent: the pair must not be counted as all replies in.
        first.onEoseCallback?.invoke(subscriptionId)
        late.onEoseCallback?.invoke(subscriptionId)
        yield()
        assertFalse(query.isCompleted, "the second relay is expected but silent")

        testScheduler.advanceUntilIdle()
        val response = query.await()

        assertFalse(response.isComplete)
    }

    @Test
    fun queryReturnsAsSoonAsTheRelayClosesTheSubscription() = runTest {
        val config = NostrConfig().apply { queryTimeoutMs = 60_000 }
        val pool = RelayPool()
        pool.sendRequest = { _, _ -> }
        pool.bindScope(this)
        val connection = pool.addRelay("wss://relay.example.invalid")
        val resource = EventResourceImpl(config, pool)

        val query = async {
            resource.queryEventsWithTimeout(
                filters = listOf(NostrFilter(kinds = listOf(EventKind.TEXT_NOTE))),
                timeoutMs = 30_000,
            )
        }
        val subscriptionId = pool.awaitSubscriptionId()
        connection.onOpenCallback?.invoke()
        testScheduler.runCurrent()
        // A relay that requires auth, rate-limits the request or rejects the
        // filter answers with CLOSED and then stays silent forever. Waiting for
        // an EOSE it will never send is what burnt the whole timeout.
        connection.onClosedCallback?.invoke(subscriptionId, "auth-required")

        val response = query.await()

        assertFalse(response.isComplete, "no relay reported its stored events in full")
        assertTrue(
            testScheduler.currentTime < 30_000,
            "the query should not wait out its timeout, but waited ${testScheduler.currentTime}ms",
        )
    }

    @Test
    fun aRelayThatClosesTheSubscriptionKeepsTheQueryIncomplete() = runTest {
        val config = NostrConfig().apply { queryTimeoutMs = 60_000 }
        val pool = RelayPool()
        pool.sendRequest = { _, _ -> }
        pool.bindScope(this)
        val first = pool.addRelay("wss://first.example")
        val second = pool.addRelay("wss://second.example")
        val resource = EventResourceImpl(config, pool)

        val query = async {
            resource.queryEventsWithTimeout(
                filters = listOf(NostrFilter(kinds = listOf(EventKind.TEXT_NOTE))),
                timeoutMs = 30_000,
            )
        }
        val subscriptionId = pool.awaitSubscriptionId()
        first.onOpenCallback?.invoke()
        second.onOpenCallback?.invoke()
        testScheduler.runCurrent()
        first.onEoseCallback?.invoke(subscriptionId)
        yield()
        assertFalse(query.isCompleted, "the second relay has not replied yet")
        // The second relay ended its subscription without reporting anything,
        // so the wait is over, but the result is still missing its events.
        second.onClosedCallback?.invoke(subscriptionId, "auth-required")

        val response = query.await()

        assertFalse(response.isComplete)
        assertTrue(
            testScheduler.currentTime < 30_000,
            "the query should not wait out its timeout, but waited ${testScheduler.currentTime}ms",
        )
    }

    @Test
    fun aRelayThatOpensAfterTheQueryCompletedIsNotWaitedFor() = runTest {
        val config = NostrConfig().apply { queryTimeoutMs = 60_000 }
        val pool = RelayPool()
        pool.sendRequest = { _, _ -> }
        pool.bindScope(this)
        val first = pool.addRelay("wss://first.example")
        val resource = EventResourceImpl(config, pool)

        val query = async {
            resource.queryEventsWithTimeout(
                filters = listOf(NostrFilter(kinds = listOf(EventKind.TEXT_NOTE))),
                timeoutMs = 30_000,
            )
        }
        val subscriptionId = pool.awaitSubscriptionId()
        first.onOpenCallback?.invoke()
        testScheduler.runCurrent()
        // The only participant answered, so the query is decided now.
        first.onEoseCallback?.invoke(subscriptionId)
        // A relay that opens right after is handed the subscription, but it
        // joined a finished query and must not extend or invalidate it.
        val late = pool.addRelay("wss://late.example")
        late.onOpenCallback?.invoke()
        testScheduler.runCurrent()

        val response = query.await()

        assertTrue(response.isComplete)
    }

    @Test
    fun aReconnectedRelayHasToAnswerTheResentRequest() = runTest {
        val config = NostrConfig().apply { queryTimeoutMs = 60_000 }
        val pool = RelayPool()
        pool.sendRequest = { _, _ -> }
        pool.bindScope(this)
        val first = pool.addRelay("wss://first.example")
        val second = pool.addRelay("wss://second.example")
        val resource = EventResourceImpl(config, pool)

        val query = async {
            resource.queryEventsWithTimeout(
                filters = listOf(NostrFilter(kinds = listOf(EventKind.TEXT_NOTE))),
                timeoutMs = 30_000,
            )
        }
        val subscriptionId = pool.awaitSubscriptionId()
        first.onOpenCallback?.invoke()
        second.onOpenCallback?.invoke()
        testScheduler.runCurrent()
        first.onEoseCallback?.invoke(subscriptionId)
        // The first relay drops and reconnects, and the pool resends its REQ.
        // The EOSE from the socket that died says nothing about the new one.
        first.onOpenCallback?.invoke()
        testScheduler.runCurrent()
        second.onEoseCallback?.invoke(subscriptionId)
        yield()
        assertFalse(
            query.isCompleted,
            "the reconnected relay has not answered the resent request",
        )

        first.onEoseCallback?.invoke(subscriptionId)

        assertTrue(query.await().isComplete)
    }

    @Test
    fun aRelayWhoseResentRequestCannotBeWrittenKeepsTheQueryIncomplete() = runTest {
        val config = NostrConfig().apply { queryTimeoutMs = 60_000 }
        val pool = RelayPool()
        var refuseWrites = false
        pool.sendRequest = { _, _ ->
            if (refuseWrites) throw IllegalStateException("socket is gone")
        }
        pool.bindScope(this)
        val first = pool.addRelay("wss://first.example")
        val second = pool.addRelay("wss://second.example")
        val resource = EventResourceImpl(config, pool)

        val query = async {
            resource.queryEventsWithTimeout(
                filters = listOf(NostrFilter(kinds = listOf(EventKind.TEXT_NOTE))),
                timeoutMs = 30_000,
            )
        }
        val subscriptionId = pool.awaitSubscriptionId()
        first.onOpenCallback?.invoke()
        second.onOpenCallback?.invoke()
        testScheduler.runCurrent()
        first.onEoseCallback?.invoke(subscriptionId)
        // The second relay's socket opens again, but the REQ cannot be written:
        // the query must not hold its timeout waiting for an answer.
        refuseWrites = true
        second.onOpenCallback?.invoke()
        testScheduler.runCurrent()

        val response = query.await()

        assertFalse(response.isComplete, "the relay never received the request")
        assertTrue(
            testScheduler.currentTime < 30_000,
            "the query should not wait for a relay that cannot be written to, but waited ${testScheduler.currentTime}ms",
        )
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
