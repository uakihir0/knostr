package work.socialhub.knostr.social

import kotlinx.coroutines.runBlocking
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
import work.socialhub.knostr.signing.Secp256k1Signer
import work.socialhub.knostr.social.internal.FeedResourceImpl
import work.socialhub.knostr.social.internal.MediaResourceImpl
import work.socialhub.knostr.social.model.NostrMedia
import work.socialhub.knostr.social.model.NostrMediaUpload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MediaPostingUnitTest {

    @Suppress("DEPRECATION")
    @Test
    fun legacyMediaResourceConstructorKeepsJvmDescriptor() {
        val nostr = fakeNostr(mutableListOf(), failPublication = false)

        MediaResourceImpl(nostr)

        val constructor = MediaResourceImpl::class.java.getConstructor(Nostr::class.java)
        assertEquals(listOf(Nostr::class.java), constructor.parameterTypes.toList())
    }

    @Test
    fun singleImagePostKeepsAltAndDoesNotDuplicateUrl() = runBlocking {
        val fixture = fixture()
        val url = "https://media.example/photo.jpg"

        val response = fixture.media.uploadAndPost(
            fileData = byteArrayOf(1),
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            content = "Already included $url",
            description = "Sunset",
        )

        assertEquals(1, fixture.published.size)
        assertEquals(1, response.data.medias.size)
        assertEquals(1, response.data.event.content.windowed(url.length).count { it == url })
        assertTrue(response.data.event.tags.single { it.first() == "imeta" }.contains("alt Sunset"))
    }

    @Test
    fun multipleImagesUploadBeforePublishingOneEvent() = runBlocking {
        val fixture = fixture()
        val uploads = listOf(
            upload("first.jpg", "First"),
            upload("second.jpg", "Second"),
        )

        val response = fixture.media.uploadManyAndPost(uploads, content = "Gallery")
        val event = response.data.event

        assertEquals(listOf("first.jpg", "second.jpg"), fixture.uploadedNames)
        assertEquals(1, fixture.published.size)
        assertEquals(2, response.data.medias.size)
        assertSame(response.data.medias.first(), response.data.media)
        assertEquals(2, event.tags.count { it.firstOrNull() == "imeta" })
        assertEquals(1, event.content.windowed(mediaUrl("first.jpg").length).count { it == mediaUrl("first.jpg") })
        assertEquals(1, event.content.windowed(mediaUrl("second.jpg").length).count { it == mediaUrl("second.jpg") })
    }

    @Test
    fun uploadFailureDoesNotPublishEvent() = runBlocking {
        val fixture = fixture(failOnFileName = "second.jpg")

        assertFailsWith<NostrException> {
            fixture.media.uploadManyAndPost(
                listOf(upload("first.jpg"), upload("second.jpg")),
            )
        }

        assertEquals(listOf("first.jpg", "second.jpg"), fixture.uploadedNames)
        assertTrue(fixture.published.isEmpty())
    }

    @Test
    fun emptyUploadUrlDoesNotPublishEvent() = runBlocking {
        val fixture = fixture(blankUrlOnFileName = "empty.jpg")

        assertFailsWith<NostrException> {
            fixture.media.uploadManyAndPost(listOf(upload("empty.jpg")))
        }

        assertTrue(fixture.published.isEmpty())
    }

    @Test
    fun blankUploadedAltFallsBackToInputDescription() = runBlocking {
        val fixture = fixture(blankAltOnFileName = "photo.jpg")

        val response = fixture.media.uploadAndPost(
            fileData = byteArrayOf(1),
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            description = "Input description",
        )

        assertEquals("Input description", response.data.media.alt)
        assertTrue(
            response.data.event.tags
                .single { it.firstOrNull() == "imeta" }
                .contains("alt Input description"),
        )
    }

    @Test
    fun relayFailureDoesNotRollBackCompletedUploads() = runBlocking {
        val fixture = fixture(failPublication = true)

        assertFailsWith<NostrException> {
            fixture.media.uploadManyAndPost(
                listOf(upload("first.jpg"), upload("second.jpg")),
            )
        }

        assertEquals(listOf("first.jpg", "second.jpg"), fixture.uploadedNames)
        assertTrue(fixture.published.isEmpty())
    }

    @Test
    fun mediaReplyCombinesNip10AndImetaTagsInOneEvent() = runBlocking {
        val fixture = fixture()
        val rootId = "2".repeat(64)
        val replyId = "3".repeat(64)

        val response = fixture.media.uploadAndReply(
            uploads = listOf(upload("first.jpg", "First"), upload("second.jpg", "Second")),
            replyToEventId = replyId,
            rootEventId = rootId,
            content = "Reply gallery",
            contentWarning = "photos",
            sensitive = true,
        )
        val event = response.data.event

        assertEquals(1, fixture.published.size)
        assertTrue(event.tags.contains(listOf("e", rootId, "", "root")))
        assertTrue(event.tags.contains(listOf("e", replyId, "", "reply")))
        assertEquals(2, event.tags.count { it.firstOrNull() == "imeta" })
        assertTrue(event.tags.contains(listOf("content-warning", "photos")))
    }

    @Test
    fun replyBlockingPassesCallerTags() {
        val fixture = fixture()
        val imeta = NostrMediaTags.imeta(media("blocking.jpg", "Blocking"))

        val event = fixture.feed.replyBlocking(
            content = "Blocking reply",
            replyToEventId = "4".repeat(64),
            tags = listOf(imeta),
        ).data

        assertTrue(event.tags.contains(imeta))
        assertTrue(event.tags.any { it.firstOrNull() == "e" })
    }

    @Suppress("DEPRECATION")
    @Test
    fun legacyReplyOverloadsDelegateToTagAwareMethods() = runBlocking {
        val fixture = fixture()
        val parentId = "5".repeat(64)

        val asyncReply = fixture.feed.reply("Legacy async reply", parentId).data
        val blockingReply = fixture.feed.replyBlocking("Legacy blocking reply", parentId).data

        assertTrue(asyncReply.tags.contains(listOf("e", parentId, "", "root")))
        assertTrue(blockingReply.tags.contains(listOf("e", parentId, "", "root")))
        assertEquals(2, fixture.published.size)
    }

    private fun fixture(
        failOnFileName: String? = null,
        blankUrlOnFileName: String? = null,
        blankAltOnFileName: String? = null,
        failPublication: Boolean = false,
    ): Fixture {
        val published = mutableListOf<NostrEvent>()
        val uploadedNames = mutableListOf<String>()
        val nostr = fakeNostr(published, failPublication)
        val config = NostrSocialConfig().apply { deferredEnrichmentEnabled = false }
        val feed = FeedResourceImpl(nostr, config)
        val media = MediaResourceImpl.withConfiguredUploader(nostr, config, feed) { input ->
            uploadedNames.add(input.fileName)
            if (input.fileName == failOnFileName) {
                throw NostrException("Upload failed")
            }
            Response(media(input.fileName, input.description).apply {
                if (input.fileName == blankUrlOnFileName) url = ""
                if (input.fileName == blankAltOnFileName) alt = " "
            })
        }
        return Fixture(feed, media, published, uploadedNames)
    }

    private fun fakeNostr(
        published: MutableList<NostrEvent>,
        failPublication: Boolean,
    ) = object : Nostr {
        private val events = object : EventResource {
            override suspend fun publishEvent(event: NostrEvent): Response<Boolean> {
                if (failPublication) throw NostrException("Relay publication failed")
                published.add(event)
                return Response(true)
            }

            override suspend fun queryEvents(filters: List<NostrFilter>) = Response<List<NostrEvent>>(listOf())
            override suspend fun deleteEvent(eventId: String, reason: String) = Response(true)
            override fun publishEventBlocking(event: NostrEvent) = runBlocking { publishEvent(event) }
            override fun queryEventsBlocking(filters: List<NostrFilter>) = Response<List<NostrEvent>>(listOf())
            override fun deleteEventBlocking(eventId: String, reason: String) = Response(true)
        }
        private val signer = Secp256k1Signer("1".repeat(64))

        override fun events() = events
        override fun relays(): RelayResource = throw NotImplementedError()
        override fun nip(): NipResource = throw NotImplementedError()
        override fun signer() = signer
        override fun config() = NostrConfig()
        override fun relayPool(): RelayPool = throw NotImplementedError()
    }

    private fun upload(fileName: String, description: String = "") = NostrMediaUpload(
        fileData = byteArrayOf(1),
        fileName = fileName,
        mimeType = "image/jpeg",
        description = description,
    )

    private fun media(fileName: String, alt: String) = NostrMedia().apply {
        url = mediaUrl(fileName)
        this.fileName = fileName
        mimeType = "image/jpeg"
        this.alt = alt
    }

    private fun mediaUrl(fileName: String) = "https://media.example/$fileName"

    private class Fixture(
        val feed: FeedResourceImpl,
        val media: MediaResourceImpl,
        val published: MutableList<NostrEvent>,
        val uploadedNames: MutableList<String>,
    )
}
