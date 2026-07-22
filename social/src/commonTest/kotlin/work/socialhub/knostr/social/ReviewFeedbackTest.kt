package work.socialhub.knostr.social

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrConfig
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.EventResource
import work.socialhub.knostr.api.NipResource
import work.socialhub.knostr.api.RelayResource
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.relay.RelayPool
import work.socialhub.knostr.signing.NostrSigner
import work.socialhub.knostr.signing.Secp256k1Signer
import work.socialhub.knostr.social.api.SocialCache
import work.socialhub.knostr.social.internal.BadgeResourceImpl
import work.socialhub.knostr.social.internal.BookmarkResourceImpl
import work.socialhub.knostr.social.internal.ChannelResourceImpl
import work.socialhub.knostr.social.internal.EnrichmentResourceImpl
import work.socialhub.knostr.social.internal.InterestResourceImpl
import work.socialhub.knostr.social.internal.SearchResourceImpl
import work.socialhub.knostr.social.internal.UserResourceImpl
import work.socialhub.knostr.social.model.SocialDataBatch
import work.socialhub.knostr.social.model.SocialDataRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ReviewFeedbackTest {
    private val pubkey = "1".repeat(64)

    @Test
    fun profileBadgesAggregateDefinitionCompleteness() = runBlocking {
        val profileBadges = event(
            kind = EventKind.PROFILE_BADGES,
            tags = listOf(listOf("a", "${EventKind.BADGE_DEFINITION}:$pubkey:founder")),
        )
        val badgeDefinition = event(
            kind = EventKind.BADGE_DEFINITION,
            tags = listOf(listOf("d", "founder"), listOf("name", "Founder")),
        )
        val events = fakeEvents(
            query = { filters ->
                when (filters.single().kinds) {
                    listOf(EventKind.PROFILE_BADGES) -> Response(listOf(profileBadges))
                    listOf(EventKind.BADGE_DEFINITION) ->
                        Response(listOf(badgeDefinition)).also { it.isComplete = false }
                    else -> Response(listOf())
                }
            },
        )

        val response = BadgeResourceImpl(fakeNostr(events)).getProfileBadges(pubkey)

        assertEquals("Founder", response.data.single().name)
        assertFalse(response.isComplete)
    }

    @Test
    fun profileBadgesPreserveColonsInDefinitionIdentifier() = runBlocking {
        val dTag = "founder:2026"
        val profileBadges = event(
            kind = EventKind.PROFILE_BADGES,
            tags = listOf(listOf("a", "${EventKind.BADGE_DEFINITION}:$pubkey:$dTag")),
        )
        val badgeDefinition = event(
            kind = EventKind.BADGE_DEFINITION,
            tags = listOf(listOf("d", dTag), listOf("name", "Founder 2026")),
        )
        val events = fakeEvents(
            query = { filters ->
                when (filters.single().kinds) {
                    listOf(EventKind.PROFILE_BADGES) -> Response(listOf(profileBadges))
                    listOf(EventKind.BADGE_DEFINITION) -> Response(listOf(badgeDefinition))
                    else -> Response(listOf())
                }
            },
        )

        val response = BadgeResourceImpl(fakeNostr(events)).getProfileBadges(pubkey)

        assertEquals(dTag, response.data.single().dTag)
        assertEquals("Founder 2026", response.data.single().name)
    }

    @Test
    fun userSearchCachesOnlyNewestMetadataPerPubkey() = runBlocking {
        val older = metadata(createdAt = 1, name = "old")
        val newer = metadata(createdAt = 2, name = "new")
        val cache = RecordingCache()
        val events = fakeEvents(query = { Response(listOf(newer, older)) })

        SearchResourceImpl(fakeNostr(events), socialCache = cache)
            .searchUsers("founder", limit = 10)

        assertEquals(1, cache.stored.users.size)
        assertEquals("new", cache.stored.users.single().name)
    }

    @Test
    fun incompleteUserSearchIsNotCachedAndQueuesRefresh() = runBlocking {
        val releaseRefresh = CompletableDeferred<Unit>()
        val cache = RecordingCache()
        val events = fakeEvents(
            query = { filters ->
                if (filters.single().search != null) {
                    Response(listOf(metadata(createdAt = 1, name = "old"))).also {
                        it.isComplete = false
                    }
                } else {
                    releaseRefresh.await()
                    Response(listOf(metadata(createdAt = 2, name = "new")))
                }
            },
        )
        val nostr = fakeNostr(events)
        val config = NostrSocialConfig().apply {
            deferredEnrichmentInitialDelayMs = 0
            deferredEnrichmentMaxAttempts = 1
        }
        val enrichment = EnrichmentResourceImpl(nostr, cache, config)
        val refreshed = CompletableDeferred<SocialDataBatch>()
        enrichment.onUpdateCallback = { batch ->
            if (batch.users.singleOrNull()?.name == "new") {
                refreshed.complete(batch)
            }
        }
        val search = SearchResourceImpl(nostr, config, cache, enrichment)

        val response = search.searchUsers("founder", limit = 10)

        assertEquals("old", response.data.single().name)
        assertFalse(response.isComplete)
        assertEquals(0, cache.putCalls)
        releaseRefresh.complete(Unit)
        assertEquals("new", withTimeout(2_000) { refreshed.await() }.users.single().name)
        assertEquals("new", cache.stored.users.single().name)
        enrichment.close()
    }

    @Test
    fun incompleteProfileQueryIsNotCachedAndQueuesRefresh() = runBlocking {
        var queries = 0
        val cache = RecordingCache()
        val events = fakeEvents(
            query = {
                queries++
                if (queries == 1) {
                    Response(listOf(metadata(createdAt = 1, name = "old"))).also {
                        it.isComplete = false
                    }
                } else {
                    Response(listOf(metadata(createdAt = 2, name = "new")))
                }
            },
        )
        val nostr = fakeNostr(events)
        val config = NostrSocialConfig().apply {
            deferredEnrichmentInitialDelayMs = 100
            deferredEnrichmentMaxAttempts = 1
        }
        val enrichment = EnrichmentResourceImpl(nostr, cache, config)
        val refreshed = CompletableDeferred<SocialDataBatch>()
        enrichment.onUpdateCallback = { batch ->
            if (batch.users.singleOrNull()?.name == "new") {
                refreshed.complete(batch)
            }
        }
        val users = UserResourceImpl(nostr, config, cache, enrichment)

        val response = users.getProfiles(listOf(pubkey))

        assertEquals("old", response.data.single().name)
        assertFalse(response.isComplete)
        assertEquals(0, cache.putCalls)
        assertEquals("new", withTimeout(2_000) { refreshed.await() }.users.single().name)
        assertEquals("new", cache.stored.users.single().name)
        enrichment.close()
    }

    @Test
    fun listMutationsRejectIncompleteSourceQueries() = runBlocking {
        var publishCalls = 0
        val events = fakeEvents(
            query = {
                Response(emptyList<NostrEvent>()).also { it.isComplete = false }
            },
            publish = {
                publishCalls++
                Response(true)
            },
        )
        val nostr = fakeNostr(events, Secp256k1Signer("1".repeat(64)))
        val bookmarks = BookmarkResourceImpl(nostr)
        val channels = ChannelResourceImpl(nostr)
        val interests = InterestResourceImpl(nostr)

        assertFailsWith<NostrException> { bookmarks.bookmark("2".repeat(64)) }
        assertFailsWith<NostrException> { bookmarks.unbookmark("2".repeat(64)) }
        assertFailsWith<NostrException> { channels.joinChannel("3".repeat(64)) }
        assertFailsWith<NostrException> { channels.leaveChannel("3".repeat(64)) }
        assertFailsWith<NostrException> { interests.followHashtag("nostr") }
        assertFailsWith<NostrException> { interests.unfollowHashtag("nostr") }
        assertEquals(0, publishCalls)
    }

    private fun event(kind: Int, tags: List<List<String>>) = NostrEvent(
        id = kind.toString().padStart(64, '0'),
        pubkey = pubkey,
        createdAt = 1,
        kind = kind,
        tags = tags,
        content = "",
        sig = "",
    )

    private fun metadata(createdAt: Long, name: String) = NostrEvent(
        id = createdAt.toString().padStart(64, '0'),
        pubkey = pubkey,
        createdAt = createdAt,
        kind = EventKind.METADATA,
        tags = listOf(),
        content = """{"name":"$name"}""",
        sig = "",
    )

    private class RecordingCache : SocialCache {
        var stored = SocialDataBatch()
        var putCalls = 0

        override suspend fun get(request: SocialDataRequest) = SocialDataBatch()

        override suspend fun put(batch: SocialDataBatch) {
            putCalls++
            stored = batch
        }

        override suspend fun remove(request: SocialDataRequest) = Unit
    }

    private fun fakeEvents(
        query: suspend (List<NostrFilter>) -> Response<List<NostrEvent>>,
        publish: suspend (NostrEvent) -> Response<Boolean> = { Response(true) },
    ) = object : EventResource {
        override suspend fun queryEvents(filters: List<NostrFilter>) = query(filters)
        override suspend fun publishEvent(event: NostrEvent) = publish(event)
        override suspend fun deleteEvent(eventId: String, reason: String) = Response(true)
        override fun queryEventsBlocking(filters: List<NostrFilter>) = runBlocking {
            queryEvents(filters)
        }
        override fun publishEventBlocking(event: NostrEvent) = runBlocking {
            publishEvent(event)
        }
        override fun deleteEventBlocking(eventId: String, reason: String) = Response(true)
    }

    private fun fakeNostr(
        eventResource: EventResource,
        signer: NostrSigner? = null,
    ) = object : Nostr {
        override fun events() = eventResource
        override fun relays(): RelayResource = throw NotImplementedError()
        override fun nip(): NipResource = throw NotImplementedError()
        override fun signer() = signer
        override fun config() = NostrConfig()
        override fun relayPool(): RelayPool = throw NotImplementedError()
    }
}
