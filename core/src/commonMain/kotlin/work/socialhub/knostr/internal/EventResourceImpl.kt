package work.socialhub.knostr.internal

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock
import work.socialhub.knostr.EventKind
import work.socialhub.knostr.NostrConfig
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.EventResource
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.relay.RelayPool
import work.socialhub.knostr.util.toBlocking

class EventResourceImpl(
    private val config: NostrConfig,
    private val relayPool: RelayPool,
) : EventResource {

    override suspend fun publishEvent(event: NostrEvent): Response<Boolean> {
        try {
            relayPool.publishEvent(event)
            return Response(true)
        } catch (e: Exception) {
            throw NostrException(e)
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    override suspend fun queryEvents(filters: List<NostrFilter>): Response<List<NostrEvent>> {
        try {
            val eventChannel = Channel<NostrEvent>(Channel.UNLIMITED)
            val eoseDeferred = CompletableDeferred<Unit>()

            val expectedEose = relayPool.getConnectedRelays().size.coerceAtLeast(1)
            val eoseCount = AtomicInt(0)

            val subId = relayPool.subscribe(
                filters = filters,
                onEvent = { event ->
                    eventChannel.trySend(event)
                },
                onEose = { _ ->
                    if (eoseCount.fetchAndAdd(1) + 1 >= expectedEose) {
                        eoseDeferred.complete(Unit)
                    }
                },
            )

            try {
                withTimeout(config.queryTimeoutMs) {
                    eoseDeferred.await()
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                // Timeout: return what we have so far
            } finally {
                relayPool.unsubscribe(subId)
            }

            eventChannel.close()
            val events = mutableListOf<NostrEvent>()
            for (event in eventChannel) {
                events.add(event)
            }
            return Response(events)
        } catch (e: Exception) {
            throw NostrException(e)
        }
    }

    override suspend fun deleteEvent(eventId: String, reason: String): Response<Boolean> {
        val signer = config.signer
            ?: throw NostrException("Signer is required to delete events")

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.EVENT_DELETION,
            tags = listOf(listOf("e", eventId)),
            content = reason,
        )
        val signed = signer.sign(unsigned)
        return publishEvent(signed)
    }

    override fun publishEventBlocking(event: NostrEvent): Response<Boolean> {
        return toBlocking { publishEvent(event) }
    }

    override fun queryEventsBlocking(filters: List<NostrFilter>): Response<List<NostrEvent>> {
        return toBlocking { queryEvents(filters) }
    }

    override fun deleteEventBlocking(eventId: String, reason: String): Response<Boolean> {
        return toBlocking { deleteEvent(eventId, reason) }
    }
}
