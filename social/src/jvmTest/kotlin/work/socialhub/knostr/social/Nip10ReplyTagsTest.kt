package work.socialhub.knostr.social

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
import work.socialhub.knostr.signing.Secp256k1Signer
import work.socialhub.knostr.social.api.SocialCache
import work.socialhub.knostr.social.internal.FeedResourceImpl
import work.socialhub.knostr.social.internal.SocialMapper
import work.socialhub.knostr.social.model.SocialDataBatch
import work.socialhub.knostr.social.model.SocialDataRequest
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Offline checks for the NIP-10 tags a reply carries: the thread root, the
 * direct parent and every participant of the thread.
 */
class Nip10ReplyTagsTest {

    private val rootId = "1".repeat(64)
    private val parentId = "2".repeat(64)
    private val rootAuthor = "a".repeat(64)
    private val parentAuthor = "b".repeat(64)
    private val otherParticipant = "c".repeat(64)

    @Test
    fun topLevelReplyMarksTheParentAsRootWithAnAuthorHint() = runBlocking {
        val fixture = fixture(parent = event(parentId, parentAuthor))

        val tags = fixture.feed.reply(
            content = "reply",
            tags = listOf(),
            replyToEventId = parentId,
        ).data.tags

        assertEquals(listOf(listOf("e", parentId, "", "root", parentAuthor)), tags.eventTags())
        assertEquals(listOf(parentAuthor), tags.participants())
    }

    @Test
    fun nestedReplyKeepsTheThreadRootAndInheritsParticipants() = runBlocking {
        val parent = event(
            parentId,
            parentAuthor,
            listOf(
                listOf("e", rootId, "", "root", rootAuthor),
                listOf("p", rootAuthor),
                listOf("p", otherParticipant),
            ),
        )
        val fixture = fixture(parent)

        val tags = fixture.feed.reply(
            content = "reply",
            tags = listOf(),
            replyToEventId = parentId,
        ).data.tags

        assertEquals(
            listOf(
                listOf("e", rootId, "", "root", rootAuthor),
                listOf("e", parentId, "", "reply", parentAuthor),
            ),
            tags.eventTags(),
        )
        assertEquals(listOf(rootAuthor, otherParticipant, parentAuthor), tags.participants())
    }

    @Test
    fun parentAuthorIsNotDuplicatedWhenAlreadyMentioned() = runBlocking {
        val parent = event(
            parentId,
            parentAuthor,
            listOf(
                listOf("e", rootId, "", "root"),
                listOf("p", parentAuthor),
            ),
        )
        val fixture = fixture(parent)

        val tags = fixture.feed.reply(
            content = "reply",
            tags = listOf(),
            replyToEventId = parentId,
        ).data.tags

        assertEquals(listOf(parentAuthor), tags.participants())
        // The root author is unknown here, so the root tag stays four elements.
        assertEquals(listOf("e", rootId, "", "root"), tags.eventTags().first())
    }

    @Test
    fun legacyPositionalParentTagsResolveTheThreadRoot() = runBlocking {
        val parent = event(
            parentId,
            parentAuthor,
            listOf(
                listOf("e", rootId),
                listOf("e", "3".repeat(64)),
            ),
        )
        val fixture = fixture(parent)

        val tags = fixture.feed.reply(
            content = "reply",
            tags = listOf(),
            replyToEventId = parentId,
        ).data.tags

        assertEquals(
            listOf(
                listOf("e", rootId, "", "root"),
                listOf("e", parentId, "", "reply", parentAuthor),
            ),
            tags.eventTags(),
        )
    }

    @Test
    fun parentWithOnlyAReplyMarkerUsesItsTargetAsRoot() = runBlocking {
        val parent = event(parentId, parentAuthor, listOf(listOf("e", rootId, "", "reply")))
        val fixture = fixture(parent)

        val tags = fixture.feed.reply(
            content = "reply",
            tags = listOf(),
            replyToEventId = parentId,
        ).data.tags

        assertEquals(rootId, tags.eventTags().first()[1])
        assertEquals(parentId, tags.eventTags().last()[1])
    }

    @Test
    fun callerSuppliedRootWinsOverTheParentTags() = runBlocking {
        val overriddenRoot = "9".repeat(64)
        val parent = event(parentId, parentAuthor, listOf(listOf("e", rootId, "", "root")))
        val fixture = fixture(parent)

        val tags = fixture.feed.reply(
            content = "reply",
            tags = listOf(),
            replyToEventId = parentId,
            rootEventId = overriddenRoot,
        ).data.tags

        assertEquals(overriddenRoot, tags.eventTags().first()[1])
        assertEquals(listOf(parentAuthor), tags.participants())
    }

    @Test
    fun cachedParentIsUsedWithoutQueryingRelays() = runBlocking {
        val fixture = fixture(parent = null, cachedParent = event(parentId, parentAuthor))

        val tags = fixture.feed.reply(
            content = "reply",
            tags = listOf(),
            replyToEventId = parentId,
        ).data.tags

        assertEquals(0, fixture.parentLookups)
        assertEquals(listOf(parentAuthor), tags.participants())
    }

    @Test
    fun unresolvableParentStillPublishesWithTheTagsWeKnow() = runBlocking {
        val fixture = fixture(parent = null)

        val tags = fixture.feed.reply(
            content = "reply",
            tags = listOf(),
            replyToEventId = parentId,
        ).data.tags

        assertEquals(1, fixture.published.size)
        assertEquals(listOf(listOf("e", parentId, "", "root")), tags.eventTags())
        assertTrue(tags.participants().isEmpty())
    }

    @Test
    fun callerTagsAndWarningsSurviveAlongsideTheThreadTags() = runBlocking {
        val fixture = fixture(parent = event(parentId, parentAuthor))
        val quoted = "8".repeat(64)

        val tags = fixture.feed.reply(
            content = "reply",
            tags = listOf(listOf("q", quoted)),
            replyToEventId = parentId,
            contentWarning = "spoiler",
        ).data.tags

        assertTrue(tags.contains(listOf("q", quoted)))
        assertTrue(tags.contains(listOf("content-warning", "spoiler")))
        assertNull(tags.firstOrNull { it.firstOrNull() == "e" && it.size >= 4 && it[3] == "reply" })
    }

    @Test
    fun slowParentLookupDoesNotHoldUpPublishing() {
        val lookupTimeoutMs = 100L
        val relayDelayMs = 10_000L
        val fixture = fixture(
            parent = event(parentId, parentAuthor),
            lookupDelayMs = relayDelayMs,
            lookupTimeoutMs = lookupTimeoutMs,
        )

        val elapsed = measureTimeMillis {
            val tags = runBlocking {
                fixture.feed.reply(
                    content = "reply",
                    tags = listOf(),
                    replyToEventId = parentId,
                ).data.tags
            }

            // The parent never arrives, so the reply falls back to the tags it
            // can derive on its own instead of waiting for the relay.
            assertEquals(listOf(listOf("e", parentId, "", "root")), tags.eventTags())
            assertTrue(tags.participants().isEmpty())
        }

        assertEquals(1, fixture.published.size)
        assertEquals(1, fixture.parentLookups)
        assertTrue(
            elapsed < lookupTimeoutMs + TIMEOUT_TOLERANCE_MS,
            "the lookup should time out near ${lookupTimeoutMs}ms, but took ${elapsed}ms",
        )
    }

    @Test
    fun aParentThatArrivesBeforeEoseStillTagsTheThread() = runBlocking {
        // A relay that never sends EOSE makes the query time out, but the
        // parent it did send is reported as an incomplete response and must
        // still be used for the tags.
        val fixture = fixture(
            parent = event(parentId, parentAuthor),
            lookupIsComplete = false,
        )

        val tags = fixture.feed.reply(
            content = "reply",
            tags = listOf(),
            replyToEventId = parentId,
        ).data.tags

        assertEquals(listOf(listOf("e", parentId, "", "root", parentAuthor)), tags.eventTags())
        assertEquals(listOf(parentAuthor), tags.participants())
    }

    private fun List<List<String>>.eventTags() = filter { it.firstOrNull() == "e" }

    private fun List<List<String>>.participants() =
        filter { it.firstOrNull() == "p" }.map { it[1] }

    private fun event(
        id: String,
        pubkey: String,
        tags: List<List<String>> = listOf(),
    ) = NostrEvent(
        id = id,
        pubkey = pubkey,
        createdAt = 1,
        kind = EventKind.TEXT_NOTE,
        tags = tags,
        content = "parent",
        sig = "",
    )

    private fun fixture(
        parent: NostrEvent?,
        cachedParent: NostrEvent? = null,
        lookupDelayMs: Long = 0,
        lookupTimeoutMs: Long? = null,
        lookupIsComplete: Boolean = true,
    ): Fixture {
        val published = mutableListOf<NostrEvent>()
        val fixture = Fixture(published)
        val events = object : EventResource {
            override suspend fun queryEvents(filters: List<NostrFilter>): Response<List<NostrEvent>> {
                // Only id lookups resolve the parent; the reply-count refresh
                // queries by tag and must not see it.
                val ids = filters.flatMap { it.ids ?: listOf() }
                if (ids.isEmpty()) return Response(listOf())
                fixture.parentLookups++
                if (lookupDelayMs > 0) delay(lookupDelayMs)
                return Response(listOfNotNull(parent).filter { it.id in ids })
                    .also { it.isComplete = lookupIsComplete }
            }

            override suspend fun publishEvent(event: NostrEvent): Response<Boolean> {
                published.add(event)
                return Response(true)
            }

            override suspend fun deleteEvent(eventId: String, reason: String) = Response(true)
            override fun queryEventsBlocking(filters: List<NostrFilter>) = runBlocking { queryEvents(filters) }
            override fun publishEventBlocking(event: NostrEvent) = runBlocking { publishEvent(event) }
            override fun deleteEventBlocking(eventId: String, reason: String) = Response(true)
        }
        val nostr = object : Nostr {
            private val signer = Secp256k1Signer("1".repeat(64))
            override fun events() = events
            override fun relays(): RelayResource = throw NotImplementedError()
            override fun nip(): NipResource = throw NotImplementedError()
            override fun signer() = signer
            override fun config() = NostrConfig()
            override fun relayPool(): RelayPool = throw NotImplementedError()
        }
        val cache = object : SocialCache {
            override suspend fun get(request: SocialDataRequest): SocialDataBatch {
                val note = cachedParent
                    ?.takeIf { it.id in request.noteIds }
                    ?.let { SocialMapper.toNote(it) }
                return SocialDataBatch(notes = listOfNotNull(note))
            }

            override suspend fun put(batch: SocialDataBatch) = Unit
            override suspend fun remove(request: SocialDataRequest) = Unit
        }
        val config = NostrSocialConfig().apply {
            deferredEnrichmentEnabled = false
            lookupTimeoutMs?.let { referencedEventLookupTimeoutMs = it }
        }
        fixture.feed = FeedResourceImpl(nostr, config, cache)
        return fixture
    }

    private class Fixture(val published: MutableList<NostrEvent>) {
        lateinit var feed: FeedResourceImpl
        var parentLookups = 0
    }

    private companion object {
        /** Slack for scheduling around the configured lookup timeout. */
        const val TIMEOUT_TOLERANCE_MS = 1_000L
    }
}
