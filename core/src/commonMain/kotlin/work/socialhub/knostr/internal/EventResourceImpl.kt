package work.socialhub.knostr.internal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
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

    override suspend fun queryEvents(filters: List<NostrFilter>): Response<List<NostrEvent>> {
        return queryEventsWithTimeout(filters, config.queryTimeoutMs)
    }

    @OptIn(ExperimentalAtomicApi::class)
    override suspend fun queryEventsWithTimeout(
        filters: List<NostrFilter>,
        timeoutMs: Long,
    ): Response<List<NostrEvent>> {
        try {
            val eventChannel = Channel<NostrEvent>(Channel.UNLIMITED)
            val allRepliedDeferred = CompletableDeferred<Unit>()

            val requestedRelays = AtomicReference(emptySet<String>())
            val repliedRelays = AtomicReference(emptySet<String>())
            val installComplete = AtomicBoolean(false)
            val sawEose = AtomicBoolean(false)

            // Replies are tracked per relay url rather than counted: a relay
            // that answers twice (an EOSE followed by a CLOSED) must not stand
            // in for one that has not answered at all. Only a relay the pool
            // actually handed the REQ to is expected to answer, and the wait
            // cannot end while any of those is silent.
            fun maybeComplete() {
                if (!installComplete.load()) return
                val requested = requestedRelays.load()
                if (requested.isEmpty()) return
                if (requested.all { it in repliedRelays.load() }) {
                    allRepliedDeferred.complete(Unit)
                }
            }

            fun markRequested(relayUrl: String) {
                while (true) {
                    val current = requestedRelays.load()
                    if (relayUrl in current) return
                    val updated = current + relayUrl
                    if (requestedRelays.compareAndSet(current, updated)) {
                        maybeComplete()
                        return
                    }
                }
            }

            fun markReplied(relayUrl: String) {
                while (true) {
                    val current = repliedRelays.load()
                    if (relayUrl in current) return
                    val updated = current + relayUrl
                    if (repliedRelays.compareAndSet(current, updated)) {
                        maybeComplete()
                        return
                    }
                }
            }

            val subId = relayPool.subscribe(
                filters = filters,
                onEvent = { event ->
                    eventChannel.trySend(event)
                },
                onEose = { relayUrl ->
                    sawEose.store(true)
                    markReplied(relayUrl)
                },
                // A relay that ends the subscription (auth required, rate
                // limited, filter rejected) never sends an EOSE. Treating that
                // as its reply is what keeps one such relay from holding every
                // query open for the whole timeout. It does not count towards
                // completeness though: a query answered only by CLOSED has no
                // relay that reported its stored events in full.
                onClosed = { relayUrl, _ ->
                    markReplied(relayUrl)
                },
                // The participants are the relays the pool hands this
                // subscription to, which includes a relay that opens while the
                // query waits. A reply from a relay that only joins later can
                // then never finish the query on behalf of a silent one.
                onRequestSent = { relayUrl ->
                    markRequested(relayUrl)
                },
            )
            // A relay that answers while the first REQs are still going out
            // must not end the wait for a relay the pool has not handed the
            // subscription to yet.
            installComplete.store(true)
            maybeComplete()

            val isComplete: Boolean
            try {
                isComplete = withTimeoutOrNull(timeoutMs) {
                    allRepliedDeferred.await()
                    sawEose.load()
                } ?: false
            } finally {
                // The caller may be cancelled by now, and the subscription must
                // still be dropped. RelayPool.unsubscribe removes the local
                // bookkeeping before it talks to the relays, so bounding the
                // wait here can at worst skip a CLOSE frame: it cannot leave
                // callbacks behind, and a stalled relay cannot stretch the
                // deadline the caller asked for.
                withContext(NonCancellable) {
                    withTimeoutOrNull(UNSUBSCRIBE_TIMEOUT_MS) {
                        relayPool.unsubscribe(subId)
                    }
                }
            }

            eventChannel.close()
            val events = mutableListOf<NostrEvent>()
            for (event in eventChannel) {
                events.add(event)
            }
            return Response<List<NostrEvent>>(events).also {
                it.isComplete = isComplete
            }
        } catch (e: CancellationException) {
            throw e
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

    override fun queryEventsWithTimeoutBlocking(
        filters: List<NostrFilter>,
        timeoutMs: Long,
    ): Response<List<NostrEvent>> {
        return toBlocking { queryEventsWithTimeout(filters, timeoutMs) }
    }

    override fun deleteEventBlocking(eventId: String, reason: String): Response<Boolean> {
        return toBlocking { deleteEvent(eventId, reason) }
    }

    private companion object {
        /** How long a finished query waits for the CLOSE frames to go out. */
        const val UNSUBSCRIBE_TIMEOUT_MS = 1_000L
    }
}
