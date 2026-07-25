package work.socialhub.knostr.social

import kotlinx.coroutines.test.runTest
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrConfig
import work.socialhub.knostr.api.NipResource
import work.socialhub.knostr.api.RelayResource
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.relay.RelayPool
import work.socialhub.knostr.social.internal.MediaEventPublisher
import work.socialhub.knostr.social.internal.MediaResourceImpl
import work.socialhub.knostr.social.model.NostrMedia
import work.socialhub.knostr.social.model.NostrMediaUpload
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaResourceJsSmokeTest {

    @Test
    fun suspendMultipleImagePostSmoke() = runTest {
        val published = mutableListOf<NostrEvent>()
        val nostr = fakeNostr()
        val config = NostrSocialConfig().apply { deferredEnrichmentEnabled = false }
        val publisher = recordingPublisher(published)
        val media = MediaResourceImpl.withDependencies(nostr, config, publisher) { input ->
            Response(NostrMedia().apply {
                url = "https://media.example/${input.fileName}"
                mimeType = input.mimeType
                alt = input.description
            })
        }

        val response = media.uploadManyAndPost(
            uploads = listOf(
                NostrMediaUpload(byteArrayOf(1), "one.jpg", "image/jpeg", "One"),
                NostrMediaUpload(byteArrayOf(2), "two.jpg", "image/jpeg", "Two"),
            ),
            content = "JS gallery",
        )

        assertEquals(1, published.size)
        assertEquals(2, response.data.medias.size)
        assertEquals(2, response.data.event.tags.count { it.firstOrNull() == "imeta" })
    }

    private fun recordingPublisher(published: MutableList<NostrEvent>) = object : MediaEventPublisher {
        override suspend fun post(
            content: String,
            tags: List<List<String>>,
            contentWarning: String?,
            expiry: Long?,
            sensitive: Boolean,
        ) = Response(event(content, tags)).also {
            published.add(it.data)
        }

        override suspend fun reply(
            content: String,
            replyToEventId: String,
            rootEventId: String?,
            tags: List<List<String>>,
            contentWarning: String?,
            expiry: Long?,
            sensitive: Boolean,
        ) = Response(event(content, tags))
    }

    private fun event(content: String, tags: List<List<String>>) = NostrEvent(
        id = "2".repeat(64),
        pubkey = "1".repeat(64),
        createdAt = 1,
        kind = 1,
        tags = tags,
        content = content,
        sig = "3".repeat(128),
    )

    private fun fakeNostr() = object : Nostr {
        override fun events() = throw NotImplementedError()
        override fun relays(): RelayResource = throw NotImplementedError()
        override fun nip(): NipResource = throw NotImplementedError()
        override fun signer() = null
        override fun config() = NostrConfig()
        override fun relayPool(): RelayPool = throw NotImplementedError()
    }
}
