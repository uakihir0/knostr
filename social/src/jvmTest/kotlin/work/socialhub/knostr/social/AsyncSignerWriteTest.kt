package work.socialhub.knostr.social

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrConfig
import work.socialhub.knostr.api.EventResource
import work.socialhub.knostr.api.NipResource
import work.socialhub.knostr.api.RelayResource
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.NostrProfile
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.relay.RelayPool
import work.socialhub.knostr.signing.NostrSigner
import kotlin.time.Clock

/**
 * Representative write operations must complete with an async-only signer
 * (NIP-07 / NIP-46) and must publish the event the signer returned — never an
 * unresolved signing request. The fake event resource asserts that every event
 * it receives for publishing was already signed.
 */
class AsyncSignerWriteTest {

    private val publicKey = "a".repeat(64)
    private val eventId = "e".repeat(64)
    private val authorPubkey = "b".repeat(64)

    private class AsyncOnlySigner(private val publicKey: String) : NostrSigner {
        val signedIds = mutableSetOf<String>()
        var signCount = 0

        override fun getPublicKey(): String = unsupported("getPublicKey")
        override fun sign(event: UnsignedEvent): NostrEvent = unsupported("sign")
        override fun computeEventId(event: UnsignedEvent): String = unsupported("computeEventId")
        override fun nip44Encrypt(plaintext: String, recipientPubkey: String): String = unsupported("nip44Encrypt")
        override fun nip44Decrypt(payload: String, senderPubkey: String): String = unsupported("nip44Decrypt")
        override fun nip04Encrypt(plaintext: String, recipientPubkey: String): String = unsupported("nip04Encrypt")
        override fun nip04Decrypt(ciphertext: String, senderPubkey: String): String = unsupported("nip04Decrypt")

        override suspend fun getPublicKeyAsync(): String = publicKey

        override suspend fun signAsync(event: UnsignedEvent): NostrEvent {
            // Suspending makes the ordering claim meaningful: publishing before
            // the signer answered cannot see the id in [signedIds] yet.
            delay(1)
            signCount += 1
            val signed = NostrEvent(
                id = "id-${signCount}",
                pubkey = publicKey,
                createdAt = event.createdAt,
                kind = event.kind,
                tags = event.tags,
                content = event.content,
                sig = "c".repeat(128),
            )
            signedIds += signed.id
            return signed
        }

        private fun unsupported(name: String): Nothing =
            throw UnsupportedOperationException("$name must not be called on an async-only signer")
    }

    private class RecordingEventResource(
        private val signer: AsyncOnlySigner,
    ) : EventResource {
        val published = mutableListOf<NostrEvent>()

        override suspend fun publishEvent(event: NostrEvent): Response<Boolean> {
            check(event.id in signer.signedIds) {
                "published an event that was not signed: ${event.id}"
            }
            published += event
            return Response(true)
        }

        override suspend fun queryEvents(filters: List<NostrFilter>): Response<List<NostrEvent>> {
            return Response(emptyList())
        }

        override suspend fun deleteEvent(eventId: String, reason: String): Response<Boolean> {
            val unsigned = UnsignedEvent(
                pubkey = signer.getPublicKeyAsync(),
                createdAt = Clock.System.now().epochSeconds,
                kind = EventKind.EVENT_DELETION,
                tags = listOf(listOf("e", eventId)),
                content = reason,
            )
            val signed = signer.signAsync(unsigned)
            check(signed.id in signer.signedIds)
            published += signed
            return Response(true)
        }

        override fun publishEventBlocking(event: NostrEvent): Response<Boolean> =
            runBlocking { publishEvent(event) }

        override fun queryEventsBlocking(filters: List<NostrFilter>): Response<List<NostrEvent>> =
            runBlocking { queryEvents(filters) }

        override fun deleteEventBlocking(eventId: String, reason: String): Response<Boolean> =
            runBlocking { deleteEvent(eventId, reason) }

        fun kinds(): List<Int> = published.map { it.kind }
    }

    private class FakeNostr(
        private val signer: AsyncOnlySigner,
        private val events: RecordingEventResource,
        private val config: NostrConfig,
    ) : Nostr {
        override fun events() = events
        override fun relays(): RelayResource = throw NotImplementedError()
        override fun nip(): NipResource = throw NotImplementedError()
        override fun signer(): NostrSigner = signer
        override fun config(): NostrConfig = config
        override fun relayPool(): RelayPool = throw NotImplementedError()
    }

    private data class Fixture(
        val signer: AsyncOnlySigner,
        val events: RecordingEventResource,
        val social: NostrSocial,
    )

    private fun fixture(): Fixture {
        val signer = AsyncOnlySigner(publicKey)
        val events = RecordingEventResource(signer)
        val nostrConfig = NostrConfig().also {
            it.relayUrls = listOf("wss://relay.example.com")
            it.signer = signer
        }
        val nostr = FakeNostr(signer, events, nostrConfig)
        val socialConfig = NostrSocialConfig().also { it.deferredEnrichmentEnabled = false }
        return Fixture(signer, events, NostrSocialFactory.instance(nostr, socialConfig))
    }

    @Test
    fun postPublishesSignedNote() = runTest {
        val (signer, events, social) = fixture()

        val response = social.feed().post(content = "hello nostr")

        assertEquals(1, signer.signCount)
        assertEquals(EventKind.TEXT_NOTE, response.data.kind)
        assertEquals("hello nostr", response.data.content)
        assertEquals(listOf(EventKind.TEXT_NOTE), events.kinds())
        assertEquals(publicKey, events.published.single().pubkey)
    }

    @Test
    fun replyRepostAndQuoteAreSigned() = runTest {
        val (signer, events, social) = fixture()

        social.feed().reply(content = "reply", replyToEventId = eventId)
        social.feed().repost(eventId)
        social.feed().quoteRepost(eventId = eventId, comment = "quote")

        assertEquals(3, signer.signCount)
        assertEquals(
            listOf(EventKind.TEXT_NOTE, EventKind.REPOST, EventKind.TEXT_NOTE),
            events.kinds(),
        )
        assertEquals("reply", events.published[0].content)
        assertEquals("", events.published[1].content)
        assertEquals("quote", events.published[2].content)
        assertTrue(events.published[0].tags.any { it.size >= 2 && it[0] == "e" && it[1] == eventId })
    }

    @Test
    fun likeReactionAndBookmarkAreSigned() = runTest {
        val (signer, events, social) = fixture()

        social.reactions().like(eventId, authorPubkey)
        social.reactions().react(eventId, authorPubkey, "🔥")
        social.bookmarks().bookmark(eventId)

        assertEquals(3, signer.signCount)
        assertEquals(listOf(EventKind.REACTION, EventKind.REACTION), events.kinds().take(2))
        assertEquals("+", events.published[0].content)
        assertEquals("🔥", events.published[1].content)
    }

    @Test
    fun followUnfollowAndProfileUpdateAreSigned() = runTest {
        val (signer, events, social) = fixture()

        social.users().follow(authorPubkey)
        social.users().unfollow(authorPubkey)
        social.users().updateProfile(NostrProfile(name = "me", about = "about"))

        assertTrue(signer.signCount >= 3)
        assertTrue(EventKind.FOLLOW_LIST in events.kinds())
        assertTrue(EventKind.METADATA in events.kinds())
    }

    @Test
    fun deletePostsADeletionEvent() = runTest {
        val (signer, events, social) = fixture()

        val response = social.feed().delete(eventId, "cleanup")

        assertTrue(response.data)
        assertEquals(1, signer.signCount)
        assertEquals(listOf(EventKind.EVENT_DELETION), events.kinds())
        assertEquals(listOf(listOf("e", eventId)), events.published.single().tags)
        assertEquals("cleanup", events.published.single().content)
    }

    @Test
    fun channelMessageIsSigned() = runTest {
        val (signer, events, social) = fixture()

        social.channels().sendMessage(eventId, "channel hello")

        assertEquals(1, signer.signCount)
        assertEquals(listOf(EventKind.CHANNEL_MESSAGE), events.kinds())
        assertEquals("channel hello", events.published.single().content)
    }
}
