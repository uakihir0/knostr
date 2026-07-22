package work.socialhub.knostr.social

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
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
import work.socialhub.knostr.social.internal.ReactionResourceImpl
import work.socialhub.knostr.social.internal.SocialMapper
import work.socialhub.knostr.social.internal.SocialStats
import work.socialhub.knostr.social.model.NostrNoteStats
import work.socialhub.knostr.social.model.SocialDataBatch
import work.socialhub.knostr.social.model.SocialDataRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun forceRefreshUpgradesPendingCacheFirstRequest() = runBlocking {
        var queries = 0
        val cacheReadStarted = CompletableDeferred<Unit>()
        val releaseCacheRead = CompletableDeferred<Unit>()
        val cachedUser = SocialMapper.toUser(metadata(pubkey, "cached"))
        val cache = object : SocialCache {
            override suspend fun get(request: SocialDataRequest): SocialDataBatch {
                cacheReadStarted.complete(Unit)
                releaseCacheRead.await()
                return SocialDataBatch(users = listOf(cachedUser))
            }

            override suspend fun put(batch: SocialDataBatch) = Unit
            override suspend fun remove(request: SocialDataRequest) = Unit
        }
        val eventResource = fakeEvents { filter ->
            if (filter.kinds == listOf(EventKind.METADATA)) {
                queries++
                Response(listOf(metadata(pubkey, "fresh")))
            } else {
                Response(listOf())
            }
        }
        val enrichment = EnrichmentResourceImpl(fakeNostr(eventResource), cache, config())
        val freshUpdate = CompletableDeferred<SocialDataBatch>()
        enrichment.onUpdateCallback = { batch ->
            if (batch.users.singleOrNull()?.name == "fresh") freshUpdate.complete(batch)
        }

        val request = SocialDataRequest(userPubkeys = listOf(pubkey))
        enrichment.request(request, forceRefresh = false)
        withTimeout(2_000) { cacheReadStarted.await() }
        enrichment.request(request, forceRefresh = true)
        releaseCacheRead.complete(Unit)
        val batch = withTimeout(2_000) { freshUpdate.await() }

        assertEquals(1, queries)
        assertEquals("fresh", batch.users.single().name)
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
    fun feedPropagatesIncompleteRelayResponse() = runBlocking {
        val note = textNote(noteId, pubkey)
        val eventResource = fakeEventsForFilters { filters ->
            val filter = filters.singleOrNull()
            if (filter?.authors == listOf(pubkey) && filter.kinds == listOf(EventKind.TEXT_NOTE)) {
                Response(listOf(note)).also { it.isComplete = false }
            } else {
                Response(listOf())
            }
        }
        val cache = RecordingCache()
        val noRetryConfig = config().apply { deferredEnrichmentMaxAttempts = 0 }
        val nostr = fakeNostr(eventResource)
        val enrichment = EnrichmentResourceImpl(nostr, cache, noRetryConfig)
        val feed = FeedResourceImpl(nostr, noRetryConfig, cache, enrichment)

        val response = feed.getUserFeed(pubkey)

        assertEquals(noteId, response.data.single().event.id)
        assertFalse(response.isComplete)
        enrichment.close()
    }

    @Test
    fun successfulDeleteInvalidatesCachedNoteAndStats() = runBlocking {
        val cache = RecordingCache()
        val nostr = fakeNostr(fakeEvents { Response(listOf()) })
        val enrichment = EnrichmentResourceImpl(nostr, cache, config())
        val feed = FeedResourceImpl(nostr, config(), cache, enrichment)

        val response = feed.delete(noteId)

        assertTrue(response.data)
        assertEquals(listOf(noteId), cache.removed.noteIds)
        assertEquals(listOf(noteId), cache.removed.noteStatsEventIds)
        enrichment.close()
    }

    @Test
    fun feedCacheLookupPropagatesCancellation() = runBlocking {
        val cache = object : SocialCache {
            override suspend fun get(request: SocialDataRequest): SocialDataBatch {
                throw CancellationException("cancelled")
            }

            override suspend fun put(batch: SocialDataBatch) = Unit
            override suspend fun remove(request: SocialDataRequest) = Unit
        }
        val nostr = fakeNostr(fakeEvents { Response(listOf()) })
        val enrichment = EnrichmentResourceImpl(nostr, cache, config())
        val feed = FeedResourceImpl(nostr, config(), cache, enrichment)

        assertFailsWith<CancellationException> {
            feed.getNote(noteId)
        }
        enrichment.close()
    }

    @Test
    fun incompleteStatsAreRetriedBeforeNotification() = runBlocking {
        var statsQueries = 0
        val firstReaction = NostrEvent(
            id = "3".repeat(64),
            pubkey = pubkey,
            createdAt = 1,
            kind = EventKind.REACTION,
            tags = listOf(listOf("e", noteId)),
            content = "+",
            sig = "",
        )
        val secondReaction = firstReaction.copy(id = "5".repeat(64))
        val eventResource = fakeEvents { filter ->
            if (EventKind.REACTION in (filter.kinds ?: listOf())) {
                statsQueries++
                Response(
                    if (statsQueries == 1) listOf(firstReaction) else listOf(secondReaction)
                ).also { it.isComplete = statsQueries > 1 }
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
        assertEquals(2, stats.likeCount)
        enrichment.close()
    }

    @Test
    fun freshCachedStatsSkipRelayQuery() = runBlocking {
        val note = textNote(noteId, pubkey)
        val partialReaction = NostrEvent(
            id = "6".repeat(64),
            pubkey = pubkey,
            createdAt = 2,
            kind = EventKind.REACTION,
            tags = listOf(listOf("e", noteId)),
            content = "+",
            sig = "",
        )
        var statsQueries = 0
        val eventResource = fakeEvents { filter ->
            when {
                filter.kinds == listOf(EventKind.TEXT_NOTE) -> Response(listOf(note))
                filter.kinds == listOf(EventKind.METADATA) ->
                    Response(listOf(metadata(pubkey, "alice")))
                filter.kinds?.contains(EventKind.REACTION) == true -> {
                    statsQueries++
                    Response(listOf(partialReaction)).also { it.isComplete = false }
                }
                else -> Response(listOf())
            }
        }
        val cache = RecordingCache(
            SocialDataBatch(
                noteStats = listOf(
                    NostrNoteStats(
                        eventId = noteId,
                        likeCount = 7,
                        replyCount = 8,
                        repostCount = 9,
                    )
                )
            )
        )
        val nostr = fakeNostr(eventResource)
        val enrichment = EnrichmentResourceImpl(nostr, cache, config())
        val feed = FeedResourceImpl(nostr, config(), cache, enrichment)

        val returned = feed.getUserFeed(pubkey).data.single()

        assertEquals(7, returned.likeCount)
        assertEquals(8, returned.replyCount)
        assertEquals(9, returned.repostCount)
        assertEquals(0, statsQueries)
        enrichment.close()
    }

    @Test
    fun markedMentionIsNotCountedAsReply() {
        val mention = textNote("8".repeat(64), pubkey).copy(
            tags = listOf(listOf("e", noteId, "", "mention"))
        )

        val stats = SocialStats.calculate(noteId, listOf(mention))

        assertEquals(0, stats.replyCount)
    }

    @Test
    fun loneRootMarkerStillCountsAsDirectReply() {
        val reply = textNote("9".repeat(64), pubkey).copy(
            tags = listOf(listOf("e", noteId, "", "root"))
        )

        val stats = SocialStats.calculate(noteId, listOf(reply))

        assertEquals(1, stats.replyCount)
    }

    @Test
    fun statisticsQueriesUseSeparateUnlimitedFilters() = runBlocking {
        val note = textNote(noteId, pubkey)
        val reactions = (1..250).map { index ->
            NostrEvent(
                id = index.toString(16).padStart(64, '0'),
                pubkey = pubkey,
                createdAt = index.toLong(),
                kind = EventKind.REACTION,
                tags = listOf(listOf("e", noteId)),
                content = "+",
                sig = "",
            )
        }
        val replies = (251..290).map { index ->
            NostrEvent(
                id = index.toString(16).padStart(64, '0'),
                pubkey = pubkey,
                createdAt = index.toLong(),
                kind = EventKind.TEXT_NOTE,
                tags = listOf(listOf("e", noteId, "", "reply")),
                content = "reply",
                sig = "",
            )
        }
        val reposts = (291..320).map { index ->
            NostrEvent(
                id = index.toString(16).padStart(64, '0'),
                pubkey = pubkey,
                createdAt = index.toLong(),
                kind = EventKind.REPOST,
                tags = listOf(listOf("e", noteId)),
                content = "",
                sig = "",
            )
        }
        var statsFilters: List<NostrFilter> = listOf()
        val eventResource = fakeEventsForFilters { filters ->
            when {
                filters.size == 3 -> {
                    statsFilters = filters
                    Response(reactions + replies + reposts)
                }
                filters.single().kinds == listOf(EventKind.TEXT_NOTE) -> Response(listOf(note))
                filters.single().kinds == listOf(EventKind.METADATA) ->
                    Response(listOf(metadata(pubkey, "alice")))
                else -> Response(listOf())
            }
        }
        val nostr = fakeNostr(eventResource)
        val cache = RecordingCache()
        val enrichment = EnrichmentResourceImpl(nostr, cache, config())
        val feed = FeedResourceImpl(nostr, config(), cache, enrichment)

        val returned = feed.getUserFeed(pubkey).data.single()

        assertEquals(3, statsFilters.size)
        assertTrue(statsFilters.all { it.limit == null })
        assertEquals(
            setOf(
                listOf(EventKind.REACTION),
                listOf(EventKind.TEXT_NOTE),
                listOf(EventKind.REPOST, EventKind.GENERIC_REPOST),
            ),
            statsFilters.map { it.kinds ?: listOf() }.toSet(),
        )
        assertEquals(250, returned.likeCount)
        assertEquals(40, returned.replyCount)
        assertEquals(30, returned.repostCount)
        enrichment.close()
    }

    @Test
    fun cachedNoteStillResolvesItsQuotedNote() = runBlocking {
        val quotedId = "7".repeat(64)
        val root = SocialMapper.toNote(
            textNote(noteId, pubkey).copy(tags = listOf(listOf("q", quotedId)))
        )
        val quoted = SocialMapper.toNote(textNote(quotedId, pubkey))
        val cache = RecordingCache(SocialDataBatch(notes = listOf(root, quoted)))
        val nostr = fakeNostr(fakeEvents { Response(listOf()) })
        val enrichment = EnrichmentResourceImpl(nostr, cache, config())
        val feed = FeedResourceImpl(nostr, config(), cache, enrichment)

        val returned = feed.getNote(noteId).data

        assertEquals(quotedId, returned.quotedNote?.event?.id)
        enrichment.close()
    }

    @Test
    fun cachedQuoteIsAttachedBeforeAuthorPopulation() = runBlocking {
        val quotedId = "7".repeat(64)
        val quotedPubkey = "8".repeat(64)
        val rootEvent = textNote(noteId, pubkey).copy(tags = listOf(listOf("q", quotedId)))
        val quoted = SocialMapper.toNote(textNote(quotedId, quotedPubkey))
        val cache = RecordingCache(SocialDataBatch(notes = listOf(quoted)))
        val eventResource = fakeEventsForFilters { filters ->
            when {
                filters.size == 3 -> Response(listOf())
                filters.single().kinds == listOf(EventKind.TEXT_NOTE) -> Response(listOf(rootEvent))
                filters.single().kinds == listOf(EventKind.METADATA) -> Response(
                    listOf(
                        metadata(pubkey, "root-author"),
                        metadata(quotedPubkey, "quoted-author"),
                    )
                )
                else -> Response(listOf())
            }
        }
        val nostr = fakeNostr(eventResource)
        val enrichment = EnrichmentResourceImpl(nostr, cache, config())
        val feed = FeedResourceImpl(nostr, config(), cache, enrichment)

        val returned = feed.getUserFeed(pubkey).data.single()

        assertEquals("quoted-author", returned.quotedNote?.author?.name)
        enrichment.close()
    }

    @Test
    fun fetchedNoteQueuesItsNestedQuoteForEnrichment() = runBlocking {
        val quotedId = "7".repeat(64)
        val root = textNote(noteId, pubkey).copy(tags = listOf(listOf("q", quotedId)))
        val quoted = textNote(quotedId, pubkey)
        val eventResource = fakeEvents { filter ->
            when {
                filter.kinds == listOf(EventKind.TEXT_NOTE) -> {
                    val ids = filter.ids.orEmpty()
                    Response(
                        when {
                            noteId in ids -> listOf(root)
                            quotedId in ids -> listOf(quoted)
                            else -> listOf()
                        }
                    )
                }
                else -> Response(listOf())
            }
        }
        val enrichment = EnrichmentResourceImpl(
            fakeNostr(eventResource),
            RecordingCache(),
            config(),
        )
        val nestedUpdate = CompletableDeferred<SocialDataBatch>()
        enrichment.onUpdateCallback = { batch ->
            if (batch.notes.any { it.event.id == quotedId }) {
                nestedUpdate.complete(batch)
            }
        }

        enrichment.request(
            SocialDataRequest(noteIds = listOf(noteId)),
            forceRefresh = true,
        )

        val batch = withTimeout(2_000) { nestedUpdate.await() }
        assertEquals(quotedId, batch.notes.single().event.id)
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

            override suspend fun remove(request: SocialDataRequest) {
                error("cache remove failed")
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
    fun cancelPendingWaitsForCanceledWorkBeforeReuse() = runBlocking {
        val cacheReadStarted = CompletableDeferred<Unit>()
        val cancellationCleanupStarted = CompletableDeferred<Unit>()
        val finishCancellationCleanup = CompletableDeferred<Unit>()
        var cacheReads = 0
        val cachedUser = SocialMapper.toUser(metadata(pubkey, "fresh"))
        val cache = object : SocialCache {
            override suspend fun get(request: SocialDataRequest): SocialDataBatch {
                cacheReads++
                if (cacheReads == 1) {
                    cacheReadStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            cancellationCleanupStarted.complete(Unit)
                            finishCancellationCleanup.await()
                        }
                    }
                }
                return SocialDataBatch(users = listOf(cachedUser))
            }

            override suspend fun put(batch: SocialDataBatch) = Unit
            override suspend fun remove(request: SocialDataRequest) = Unit
        }
        val enrichment = EnrichmentResourceImpl(
            fakeNostr(fakeEvents { Response(listOf()) }),
            cache,
            config(),
        )

        enrichment.request(SocialDataRequest(userPubkeys = listOf(pubkey)), forceRefresh = false)
        withTimeout(2_000) { cacheReadStarted.await() }
        val cancellation = async { enrichment.cancelPending() }
        withTimeout(2_000) { cancellationCleanupStarted.await() }
        assertFalse(cancellation.isCompleted)
        finishCancellationCleanup.complete(Unit)
        withTimeout(2_000) { cancellation.await() }

        val update = CompletableDeferred<SocialDataBatch>()
        enrichment.onUpdateCallback = { batch ->
            if (batch.users.isNotEmpty()) update.complete(batch)
        }
        enrichment.request(SocialDataRequest(userPubkeys = listOf(pubkey)), forceRefresh = false)

        assertEquals("fresh", withTimeout(2_000) { update.await() }.users.single().name)
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

    @Test
    fun reactionAuthorLookupPropagatesCancellation() = runBlocking {
        val reaction = NostrEvent(
            id = "3".repeat(64),
            pubkey = pubkey,
            createdAt = 1,
            kind = EventKind.REACTION,
            tags = listOf(listOf("e", noteId)),
            content = "+",
            sig = "",
        )
        val cache = object : SocialCache {
            override suspend fun get(request: SocialDataRequest): SocialDataBatch {
                throw CancellationException("cancelled")
            }

            override suspend fun put(batch: SocialDataBatch) = Unit
            override suspend fun remove(request: SocialDataRequest) = Unit
        }
        val nostr = fakeNostr(fakeEvents { Response(listOf(reaction)) })
        val enrichment = EnrichmentResourceImpl(nostr, cache, config())
        val reactions = ReactionResourceImpl(nostr, config(), cache, enrichment)

        assertFailsWith<CancellationException> {
            reactions.getReactions(noteId)
        }
        enrichment.close()
    }

    @Test
    fun concurrentCachedStatsAdjustmentsDoNotLoseUpdates() = runBlocking {
        var stored = NostrNoteStats(noteId, likeCount = 1, replyCount = 2, repostCount = 3)
        val cache = object : SocialCache {
            override suspend fun get(request: SocialDataRequest): SocialDataBatch {
                val snapshot = stored
                yield()
                return SocialDataBatch(noteStats = listOf(snapshot))
            }

            override suspend fun put(batch: SocialDataBatch) {
                yield()
                stored = batch.noteStats.single()
            }

            override suspend fun remove(request: SocialDataRequest) = Unit
        }
        val nostr = fakeNostr(fakeEvents { Response(listOf()) })
        val enrichment = EnrichmentResourceImpl(nostr, cache, config())

        coroutineScope {
            val like = async {
                SocialStats.adjustCached(cache, enrichment, noteId) {
                    NostrNoteStats(it.eventId, it.likeCount + 1, it.replyCount, it.repostCount)
                }
            }
            val reply = async {
                SocialStats.adjustCached(cache, enrichment, noteId) {
                    NostrNoteStats(it.eventId, it.likeCount, it.replyCount + 1, it.repostCount)
                }
            }
            like.await()
            reply.await()
        }

        assertEquals(2, stored.likeCount)
        assertEquals(3, stored.replyCount)
        assertEquals(3, stored.repostCount)
        enrichment.close()
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
        fakeEventsForFilters { filters -> query(filters.first()) }

    private fun fakeEventsForFilters(
        query: suspend (List<NostrFilter>) -> Response<List<NostrEvent>>,
    ) =
        object : EventResource {
            override suspend fun queryEvents(filters: List<NostrFilter>) = query(filters)
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
        var removed = SocialDataRequest()

        override suspend fun get(request: SocialDataRequest): SocialDataBatch = loaded

        override suspend fun put(batch: SocialDataBatch) {
            stored = batch
        }

        override suspend fun remove(request: SocialDataRequest) {
            removed = request
        }
    }
}
