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
import work.socialhub.knostr.signing.NostrSigner
import work.socialhub.knostr.social.internal.FeedResourceImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Deterministic tests for author population in the feed, backed by a fake
 * relay so they don't depend on live relay availability/timing.
 */
class FeedResourceAuthorTest {

    // 32-byte hex pubkeys (valid for Bech32 npub encoding in the mapper).
    private val alicePubkey = "1".repeat(64)
    private val bobPubkey = "2".repeat(64)

    private fun textNote(id: String, pubkey: String, content: String) = NostrEvent(
        id = id,
        pubkey = pubkey,
        createdAt = 1_700_000_000L,
        kind = EventKind.TEXT_NOTE,
        tags = listOf(),
        content = content,
        sig = "",
    )

    private fun metadata(pubkey: String, name: String, createdAt: Long) = NostrEvent(
        id = pubkey + createdAt, // arbitrary unique id
        pubkey = pubkey,
        createdAt = createdAt,
        kind = EventKind.METADATA,
        tags = listOf(),
        content = """{"name":"$name","display_name":"$name display"}""",
        sig = "",
    )

    /** A fake Nostr whose events() answers queries from a canned list. */
    private fun fakeNostr(
        textNotes: List<NostrEvent>,
        metadataEvents: List<NostrEvent>,
    ): Nostr = object : Nostr {
        private val eventResource = object : EventResource {
            override suspend fun queryEvents(filters: List<NostrFilter>): Response<List<NostrEvent>> {
                val kinds = filters.firstOrNull()?.kinds ?: listOf()
                val authors = filters.firstOrNull()?.authors
                return when {
                    EventKind.METADATA in kinds -> {
                        val matched = metadataEvents.filter { authors == null || it.pubkey in authors }
                        Response(matched)
                    }
                    EventKind.TEXT_NOTE in kinds -> {
                        val matched = textNotes.filter { authors == null || it.pubkey in authors }
                        Response(matched)
                    }
                    // Reactions etc. — return nothing.
                    else -> Response(listOf())
                }
            }

            override suspend fun publishEvent(event: NostrEvent) = Response(true)
            override suspend fun deleteEvent(eventId: String, reason: String) = Response(true)
            override fun publishEventBlocking(event: NostrEvent) = Response(true)
            override fun queryEventsBlocking(filters: List<NostrFilter>) =
                runBlocking { queryEvents(filters) }
            override fun deleteEventBlocking(eventId: String, reason: String) = Response(true)
        }

        override fun events(): EventResource = eventResource
        override fun relays(): RelayResource = throw NotImplementedError()
        override fun nip(): NipResource = throw NotImplementedError()
        override fun signer(): NostrSigner? = null
        override fun config(): NostrConfig = NostrConfig()
        override fun relayPool(): RelayPool = throw NotImplementedError()
    }

    @Test
    fun userFeedResolvesAuthorFromMetadata() = runBlocking {
        val notes = listOf(
            textNote("a1", alicePubkey, "hello from alice"),
            textNote("a2", alicePubkey, "another alice note"),
        )
        val meta = listOf(metadata(alicePubkey, "alice", 1_700_000_100L))
        val feed = FeedResourceImpl(fakeNostr(notes, meta))

        val result = feed.getUserFeed(alicePubkey).data

        assertEquals(2, result.size)
        result.forEach { note ->
            assertNotNull(note.author, "author should be populated")
            assertEquals(alicePubkey, note.author!!.pubkey)
            assertEquals("alice", note.author!!.name)
            assertEquals("alice display", note.author!!.displayName)
        }
    }

    @Test
    fun authorIsNullWhenNoMetadataAvailable() = runBlocking {
        val notes = listOf(textNote("a1", alicePubkey, "hello"))
        // No metadata events at all.
        val feed = FeedResourceImpl(fakeNostr(notes, metadataEvents = listOf()))

        val result = feed.getUserFeed(alicePubkey).data

        assertEquals(1, result.size)
        assertNull(result[0].author, "author should stay null when no kind:0 is found")
    }

    @Test
    fun latestMetadataWinsPerPubkey() = runBlocking {
        val notes = listOf(textNote("a1", alicePubkey, "hi"))
        // Two metadata events for the same pubkey; the newer one should win.
        val meta = listOf(
            metadata(alicePubkey, "old-name", 1_700_000_000L),
            metadata(alicePubkey, "new-name", 1_700_000_500L),
        )
        val feed = FeedResourceImpl(fakeNostr(notes, meta))

        val result = feed.getUserFeed(alicePubkey).data

        assertEquals("new-name", result[0].author!!.name)
    }
}
