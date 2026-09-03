package work.socialhub.knostr.social

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
import work.socialhub.knostr.social.internal.FeedResourceImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Offline checks for the NIP-18 tags a repost / quote repost carries: the relay
 * hint, the referenced author pubkey and the author "p" tag that makes the
 * repost reach the original author.
 */
class Nip18RepostTagsTest {

    private val targetId = "2".repeat(64)
    private val targetAuthor = "b".repeat(64)
    private val relay = "wss://relay.example"

    @Test
    fun repostTagsTheRepostedAuthorAndSuggestsARelay() = runBlocking {
        val fixture = fixture(target = event(targetId, targetAuthor), relayUrls = listOf(relay))

        val tags = fixture.feed.repost(targetId).data.tags

        assertEquals(listOf("e", targetId, relay), tags.first())
        assertEquals(listOf(listOf("p", targetAuthor)), tags.filter { it.firstOrNull() == "p" })
    }

    @Test
    fun repostWithoutConfiguredRelaysLeavesAnEmptyHint() = runBlocking {
        val fixture = fixture(target = event(targetId, targetAuthor))

        val tags = fixture.feed.repost(targetId).data.tags

        assertEquals(listOf("e", targetId, ""), tags.first())
    }

    @Test
    fun repostOfAnUnresolvableEventStillPublishes() = runBlocking {
        val fixture = fixture(target = null)

        val event = fixture.feed.repost(targetId).data

        assertEquals(1, fixture.published.size)
        assertEquals(EventKind.REPOST, event.kind)
        assertEquals(listOf(listOf("e", targetId, "")), event.tags)
    }

    @Test
    fun quoteRepostCarriesTheRelayHintAuthorAndParticipant() = runBlocking {
        val fixture = fixture(target = event(targetId, targetAuthor), relayUrls = listOf(relay))

        val tags = fixture.feed.quoteRepost(targetId, "quoting").data.tags

        assertTrue(tags.contains(listOf("q", targetId, relay, targetAuthor)))
        assertTrue(tags.contains(listOf("p", targetAuthor)))
    }

    @Test
    fun quoteRepostOfAnUnresolvableEventKeepsTheShortQTag() = runBlocking {
        val fixture = fixture(target = null)

        val tags = fixture.feed.quoteRepost(targetId, "quoting", contentWarning = "spoiler").data.tags

        assertEquals(listOf("q", targetId, ""), tags.first())
        assertTrue(tags.none { it.firstOrNull() == "p" })
        assertTrue(tags.contains(listOf("content-warning", "spoiler")))
    }

    @Test
    fun replyTagsAlsoSuggestTheConfiguredRelay() = runBlocking {
        val fixture = fixture(target = event(targetId, targetAuthor), relayUrls = listOf(relay))

        val tags = fixture.feed.reply(
            content = "reply",
            tags = listOf(),
            replyToEventId = targetId,
        ).data.tags

        assertEquals(listOf("e", targetId, relay, "root", targetAuthor), tags.first())
    }

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
        content = "target",
        sig = "",
    )

    private fun fixture(
        target: NostrEvent?,
        relayUrls: List<String> = listOf(),
    ): Fixture {
        val published = mutableListOf<NostrEvent>()
        val events = object : EventResource {
            override suspend fun queryEvents(filters: List<NostrFilter>): Response<List<NostrEvent>> {
                val ids = filters.flatMap { it.ids ?: listOf() }
                if (ids.isEmpty()) return Response(listOf())
                return Response(listOfNotNull(target).filter { it.id in ids })
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
            private val config = NostrConfig().apply { this.relayUrls = relayUrls }
            override fun events() = events
            override fun relays(): RelayResource = throw NotImplementedError()
            override fun nip(): NipResource = throw NotImplementedError()
            override fun signer() = signer
            override fun config() = config
            override fun relayPool(): RelayPool = throw NotImplementedError()
        }
        val config = NostrSocialConfig().apply { deferredEnrichmentEnabled = false }
        return Fixture(FeedResourceImpl(nostr, config), published)
    }

    private class Fixture(
        val feed: FeedResourceImpl,
        val published: MutableList<NostrEvent>,
    )
}
