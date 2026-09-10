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

            // A query's participants are the relays the pool hands the
            // subscription to, not the ones that happened to be open when it
            // started: a relay that opens while the query waits is part of it
            // too. Enrollment and completion share one atomically swapped
            // state, so a relay can never join a query whose wait was just
            // declared over, and the wait cannot end while any enrolled relay
            // is silent.
            val replies = AtomicReference(QueryReplies())
            val installComplete = AtomicBoolean(false)

            fun maybeComplete() {
                if (!installComplete.load()) return
                while (true) {
                    val current = replies.load()
                    if (current.sealed || current.requested.isEmpty()) return
                    if (!current.requested.all { it in current.replied }) return
                    val sealed = current.copy(
                        sealed = true,
                        complete = current.requested.all { it in current.eose },
                    )
                    if (replies.compareAndSet(current, sealed)) {
                        allRepliedDeferred.complete(Unit)
                        return
                    }
                }
            }

            fun markRequesting(relayUrl: String) {
                while (true) {
                    val current = replies.load()
                    if (current.sealed) return
                    // A resend starts a new reply generation for the relay: an
                    // EOSE it sent over the socket that just died says nothing
                    // about the socket the pool is writing to now.
                    val updated = current.copy(
                        requested = current.requested + relayUrl,
                        replied = current.replied - relayUrl,
                        eose = current.eose - relayUrl,
                    )
                    if (updated == current) return
                    if (replies.compareAndSet(current, updated)) {
                        maybeComplete()
                        return
                    }
                }
            }

            // A relay is only expected to answer once the pool has actually
            // handed it the REQ. Replies are tracked per relay url rather than
            // counted: a relay that answers twice (an EOSE followed by a
            // CLOSED) must not stand in for one that has not answered at all.
            fun markReplied(relayUrl: String, eose: Boolean) {
                while (true) {
                    val current = replies.load()
                    val updated = current.copy(
                        replied = current.replied + relayUrl,
                        eose = if (eose) current.eose + relayUrl else current.eose,
                    )
                    if (updated == current) return
                    if (replies.compareAndSet(current, updated)) {
                        maybeComplete()
                        return
                    }
                }
            }

            // The REQ never reached this relay, so it will not answer and the
            // wait can move on. It reported nothing in full, so the result
            // stays partial: this counts as a reply without an EOSE.
            fun markRequestFailed(relayUrl: String) {
                while (true) {
                    val current = replies.load()
                    if (current.sealed || relayUrl !in current.requested) return
                    val updated = current.copy(
                        replied = current.replied + relayUrl,
                        eose = current.eose - relayUrl,
                    )
                    if (updated == current) return
                    if (replies.compareAndSet(current, updated)) {
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
                    markReplied(relayUrl, eose = true)
                },
                // A relay that ends the subscription (auth required, rate
                // limited, filter rejected) never sends an EOSE. Treating that
                // as its reply is what keeps one such relay from holding every
                // query open for the whole timeout, but it does not count
                // towards completeness: that relay did not report its stored
                // events in full, so the result stays partial.
                onClosed = { relayUrl, _ ->
                    markReplied(relayUrl, eose = false)
                },
                // The participants are the relays the pool hands this
                // subscription to, which includes a relay that opens while the
                // query waits. Enrollment happens before the REQ is written,
                // so a reply from another relay cannot seal the query in the
                // gap and shut out a relay that is being asked right now.
                onRequestSending = { relayUrl ->
                    markRequesting(relayUrl)
                },
                onRequestFailed = { relayUrl, _ ->
                    markRequestFailed(relayUrl)
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
                    replies.load().complete
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

    /**
     * Atomically swapped reply bookkeeping for one query.
     *
     * [requested] holds every relay the pool was told to hand the subscription
     * to, [replied] those that answered at all, and [eose] those that reported
     * their stored events in full. [sealed] freezes the participant set once
     * the wait is over, so a relay that opens later cannot join a finished
     * query, and [complete] is the answer the query returns: every participant
     * sent EOSE.
     */
    private data class QueryReplies(
        val requested: Set<String> = emptySet(),
        val replied: Set<String> = emptySet(),
        val eose: Set<String> = emptySet(),
        val sealed: Boolean = false,
        val complete: Boolean = false,
    )

    private companion object {
        /** How long a finished query waits for the CLOSE frames to go out. */
        const val UNSUBSCRIBE_TIMEOUT_MS = 1_000L
    }
}
