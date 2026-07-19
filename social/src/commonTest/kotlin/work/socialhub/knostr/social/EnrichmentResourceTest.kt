package work.socialhub.knostr.social

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrConfig
import work.socialhub.knostr.api.EventResource
import work.socialhub.knostr.api.NipResource
import work.socialhub.knostr.api.RelayResource
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.relay.RelayPool
import work.socialhub.knostr.signing.NostrSigner
import work.socialhub.knostr.social.api.SocialCache
import work.socialhub.knostr.social.internal.EnrichmentResourceImpl
import work.socialhub.knostr.social.internal.FeedResourceImpl
import work.socialhub.knostr.social.model.SocialDataBatch
import work.socialhub.knostr.social.model.SocialDataRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class EnrichmentResourceTest {
    private val pubkey = "1".repeat(64)
    private val noteId = "2".repeat(64)

    @Test
    fun retriesMissingUserAndWritesThrough() = runBlocking {
        var metadataQueries = 0
        val eventResource = fakeEvents { filter ->
            if (filter.kinds == listOf(EventKind.METADATA)) {
                metadataQueries++
                if (metadataQueries == 1) {
                    Response(listOf())
                } else {
                    Response(listOf(metadata(pubkey, "alice")))
                }
            } else {
                Response(listOf())
            }
        }
        val cache = RecordingCache()
        val enrichment = EnrichmentResourceImpl(fakeNostr(eventResource), cache, config())
        val update = CompletableDeferred<SocialDataBatch>()
        enrichment.onUpdateCallback = { batch ->
            if (batch.users.isNotEmpty()) update.complete(batch)
        }

        enrichment.request(SocialDataRequest(userPubkeys = listOf(pubkey)), forceRefresh = true)
        val batch = withTimeout(2_000) { update.await() }

        assertEquals(2, metadataQueries)
        assertEquals("alice", batch.users.single().name)
        assertEquals("alice", cache.stored.users.single().name)
        enrichment.close()
    }

    @Test
    fun nonForcedRequestUsesCacheWithoutRelayQuery() = runBlocking {
        var queries = 0
        val eventResource = fakeEvents {
            queries++
            Response(listOf())
        }
        val cachedUser = work.socialhub.knostr.social.internal.SocialMapper.toUser(metadata(pubkey, "cached"))
        val cache = RecordingCache(SocialDataBatch(users = listOf(cachedUser)))
        val enrichment = EnrichmentResourceImpl(fakeNostr(eventResource), cache, config())
        val update = CompletableDeferred<SocialDataBatch>()
        enrichment.onUpdateCallback = { update.complete(it) }

        enrichment.request(SocialDataRequest(userPubkeys = listOf(pubkey)), forceRefresh = false)
        val batch = withTimeout(2_000) { update.await() }

        assertEquals("cached", batch.users.single().name)
        assertEquals(0, queries)
        enrichment.close()
    }

    @Test
    fun feedKeepsPlaceholderAndPublishesLaterProfile() = runBlocking {
        var metadataQueries = 0
        val note = textNote(noteId, pubkey)
        val eventResource = fakeEvents { filter ->
            when {
                filter.kinds == listOf(EventKind.METADATA) -> {
                    metadataQueries++
                    if (metadataQueries <= 2) Response(listOf())
                    else Response(listOf(metadata(pubkey, "late-alice")))
                }
                filter.kinds == listOf(EventKind.TEXT_NOTE) -> Response(listOf(note))
                else -> Response(listOf())
            }
        }
        val cache = RecordingCache()
        val nostr = fakeNostr(eventResource)
        val enrichment = EnrichmentResourceImpl(nostr, cache, config())
        val update = CompletableDeferred<SocialDataBatch>()
        enrichment.onUpdateCallback = { batch ->
            if (batch.users.isNotEmpty()) update.complete(batch)
        }
        val feed = FeedResourceImpl(nostr, config(), cache, enrichment)

        val returnedNote = feed.getUserFeed(pubkey).data.single()
        val placeholderName = returnedNote.author?.name
        val batch = withTimeout(2_000) { update.await() }

        assertEquals(pubkey.take(8) + "...", placeholderName)
        assertEquals("late-alice", batch.users.single().name)
        assertEquals(placeholderName, returnedNote.author?.name)
        enrichment.close()
    }

    @Test
    fun incompleteStatsAreRetriedBeforeNotification() = runBlocking {
        var statsQueries = 0
        val reaction = NostrEvent(
            id = "3".repeat(64),
            pubkey = pubkey,
            createdAt = 1,
            kind = EventKind.REACTION,
            tags = listOf(listOf("e", noteId)),
            content = "+",
            sig = "",
        )
        val eventResource = fakeEvents { filter ->
            if (EventKind.REACTION in (filter.kinds ?: listOf())) {
                statsQueries++
                Response(listOf(reaction)).also { it.isComplete = statsQueries > 1 }
            } else {
                Response(listOf())
            }
        }
        val enrichment = EnrichmentResourceImpl(
            fakeNostr(eventResource),
            RecordingCache(),
            config(),
        )
        val update = CompletableDeferred<SocialDataBatch>()
        enrichment.onUpdateCallback = { batch ->
            if (batch.noteStats.isNotEmpty()) update.complete(batch)
        }

        enrichment.request(
            SocialDataRequest(noteStatsEventIds = listOf(noteId)),
            forceRefresh = true,
        )
        val stats = withTimeout(2_000) { update.await() }.noteStats.single()

        assertEquals(2, statsQueries)
        assertEquals(1, stats.likeCount)
        enrichment.close()
    }

    @Test
    fun cacheFailureFallsBackToRelayAndStillNotifies() = runBlocking {
        val eventResource = fakeEvents { filter ->
            if (filter.kinds == listOf(EventKind.METADATA)) {
                Response(listOf(metadata(pubkey, "relay-user")))
            } else {
                Response(listOf())
            }
        }
        val failingCache = object : SocialCache {
            override suspend fun get(request: SocialDataRequest): SocialDataBatch {
                error("cache read failed")
            }

            override suspend fun put(batch: SocialDataBatch) {
                error("cache write failed")
            }
        }
        val enrichment = EnrichmentResourceImpl(fakeNostr(eventResource), failingCache, config())
        val update = CompletableDeferred<SocialDataBatch>()
        enrichment.onUpdateCallback = { batch ->
            if (batch.users.isNotEmpty()) update.complete(batch)
        }

        enrichment.request(SocialDataRequest(userPubkeys = listOf(pubkey)), forceRefresh = false)
        val batch = withTimeout(2_000) { update.await() }

        assertEquals("relay-user", batch.users.single().name)
        enrichment.close()
    }

    @Test
    fun targetCanBeRequestedAgainAfterAttemptsAreExhausted() = runBlocking {
        var metadataQueries = 0
        val firstAttempt = CompletableDeferred<Unit>()
        val eventResource = fakeEvents { filter ->
            if (filter.kinds == listOf(EventKind.METADATA)) {
                metadataQueries++
                if (metadataQueries == 1) {
                    firstAttempt.complete(Unit)
                    Response(listOf())
                } else {
                    Response(listOf(metadata(pubkey, "second-cycle")))
                }
            } else {
                Response(listOf())
            }
        }
        val oneAttemptConfig = config().apply { deferredEnrichmentMaxAttempts = 1 }
        val enrichment = EnrichmentResourceImpl(
            fakeNostr(eventResource),
            RecordingCache(),
            oneAttemptConfig,
        )
        val update = CompletableDeferred<SocialDataBatch>()
        enrichment.onUpdateCallback = { batch ->
            if (batch.users.isNotEmpty()) update.complete(batch)
        }

        enrichment.request(SocialDataRequest(userPubkeys = listOf(pubkey)), forceRefresh = true)
        withTimeout(2_000) { firstAttempt.await() }
        delay(50)
        enrichment.request(SocialDataRequest(userPubkeys = listOf(pubkey)), forceRefresh = true)
        val batch = withTimeout(2_000) { update.await() }

        assertEquals("second-cycle", batch.users.single().name)
        enrichment.close()
    }

    @Test
    fun closeRejectsFurtherRequests() = runBlocking {
        var queries = 0
        val enrichment = EnrichmentResourceImpl(
            fakeNostr(
                fakeEvents {
                    queries++
                    Response(listOf())
                }
            ),
            RecordingCache(),
            config(),
        )

        enrichment.close()
        enrichment.request(SocialDataRequest(userPubkeys = listOf(pubkey)), forceRefresh = true)
        delay(20)

        assertEquals(0, queries)
    }

    private fun config() = NostrSocialConfig().apply {
        deferredEnrichmentInitialDelayMs = 0
        deferredEnrichmentMaxAttempts = 3
    }

    private fun metadata(pubkey: String, name: String) = NostrEvent(
        id = "4".repeat(64),
        pubkey = pubkey,
        createdAt = 2,
        kind = EventKind.METADATA,
        tags = listOf(),
        content = """{"name":"$name"}""",
        sig = "",
    )

    private fun textNote(id: String, pubkey: String) = NostrEvent(
        id = id,
        pubkey = pubkey,
        createdAt = 1,
        kind = EventKind.TEXT_NOTE,
        tags = listOf(),
        content = "hello",
        sig = "",
    )

    private fun fakeEvents(query: suspend (NostrFilter) -> Response<List<NostrEvent>>) =
        object : EventResource {
            override suspend fun queryEvents(filters: List<NostrFilter>) = query(filters.first())
            override suspend fun publishEvent(event: NostrEvent) = Response(true)
            override suspend fun deleteEvent(eventId: String, reason: String) = Response(true)
            override fun queryEventsBlocking(filters: List<NostrFilter>) = runBlocking {
                queryEvents(filters)
            }
            override fun publishEventBlocking(event: NostrEvent) = Response(true)
            override fun deleteEventBlocking(eventId: String, reason: String) = Response(true)
        }

    private fun fakeNostr(eventResource: EventResource) = object : Nostr {
        override fun events() = eventResource
        override fun relays(): RelayResource = throw NotImplementedError()
        override fun nip(): NipResource = throw NotImplementedError()
        override fun signer(): NostrSigner? = null
        override fun config() = NostrConfig()
        override fun relayPool(): RelayPool = throw NotImplementedError()
    }

    private class RecordingCache(
        private val loaded: SocialDataBatch = SocialDataBatch(),
    ) : SocialCache {
        var stored = SocialDataBatch()

        override suspend fun get(request: SocialDataRequest): SocialDataBatch = loaded

        override suspend fun put(batch: SocialDataBatch) {
            stored = batch
        }
    }
}
