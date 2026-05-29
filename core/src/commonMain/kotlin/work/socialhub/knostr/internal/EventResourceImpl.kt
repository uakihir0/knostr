package work.socialhub.knostr.internal

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
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
        try {
            val events = mutableListOf<NostrEvent>()
            val eoseDeferred = CompletableDeferred<Unit>()

            // Wait for EOSE from every relay that was connected when we
            // subscribed — not just the first. A relay that holds none of the
            // requested events (e.g. a missing kind:0 profile) replies EOSE
            // immediately, so completing on the first EOSE would cut the query
            // short before slower relays that actually have the event respond.
            val expectedEose = relayPool.getConnectedRelays().size.coerceAtLeast(1)
            val eosedRelays = mutableSetOf<String>()

            val subId = relayPool.subscribe(
                filters = filters,
                onEvent = { event -> events.add(event) },
                onEose = { relayUrl ->
                    eosedRelays.add(relayUrl)
                    if (eosedRelays.size >= expectedEose) {
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

            return Response(events.toList())
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
