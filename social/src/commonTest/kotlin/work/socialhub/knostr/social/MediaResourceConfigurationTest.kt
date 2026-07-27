package work.socialhub.knostr.social

import work.socialhub.knostr.NostrException
import work.socialhub.knostr.social.internal.MediaResourceImpl
import work.socialhub.knostr.social.model.NostrMedia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MediaResourceConfigurationTest {

    @Test
    fun mediaUploadServerDefaultsToNostrBuild() {
        assertEquals(
            "https://nostr.build",
            NostrSocialConfig().mediaUploadServerUrl,
        )
    }

    @Test
    fun configuredServerUrlSupportsAccountSpecificServer() {
        val config = NostrSocialConfig().apply {
            mediaUploadServerUrl = " https://media.example.com/ "
        }

        assertEquals(
            "https://media.example.com",
            MediaResourceImpl.configuredServerUrl(config),
        )
    }

    @Test
    fun configuredServerUrlRejectsBlankValue() {
        val config = NostrSocialConfig().apply {
            mediaUploadServerUrl = " "
        }

        assertFailsWith<NostrException> {
            MediaResourceImpl.configuredServerUrl(config)
        }
    }

    @Test
    fun imagePostContentIncludesUploadedUrlOnce() {
        val url = "https://media.example.com/photo.jpg"
        val largerUrl = "$url?large"

        assertEquals(url, MediaResourceImpl.appendMediaUrls("", listOf(url)))
        assertEquals(
            "A photo\n$url",
            MediaResourceImpl.appendMediaUrls("A photo", listOf(url)),
        )
        assertEquals(
            "Already here: $url",
            MediaResourceImpl.appendMediaUrls("Already here: $url", listOf(url, url)),
        )
        assertEquals(
            "See ($url)",
            MediaResourceImpl.appendMediaUrls("See ($url)", listOf(url)),
        )
        assertEquals(
            "See $url.",
            MediaResourceImpl.appendMediaUrls("See $url.", listOf(url)),
        )
        assertEquals(
            "$largerUrl\n$url",
            MediaResourceImpl.appendMediaUrls(largerUrl, listOf(url)),
        )
    }

    @Test
    fun imagePostBuildsNip92ImetaTag() {
        val media = NostrMedia().apply {
            url = "https://media.example.com/photo.jpg"
            mimeType = "image/jpeg"
            sha256 = "abc123"
            sizeBytes = 42
            width = 640
            height = 480
            blurhash = "hash"
            thumbnailUrl = "https://media.example.com/thumb.jpg"
            alt = "Sunset"
        }

        assertEquals(
            listOf(
                "imeta",
                "url https://media.example.com/photo.jpg",
                "m image/jpeg",
                "x abc123",
                "size 42",
                "dim 640x480",
                "blurhash hash",
                "thumb https://media.example.com/thumb.jpg",
                "alt Sunset",
            ),
            NostrMediaTags.imeta(media),
        )
    }

    @Test
    fun nip94ResponseUsesHostedHashAndServerAlt() {
        val media = NostrMedia()

        MediaResourceImpl.applyNip94Tags(
            media,
            listOf(
                listOf("ox", "original-hash"),
                listOf("x", "hosted-hash"),
                listOf("alt", "Server description"),
            ),
        )

        assertEquals("hosted-hash", media.sha256)
        assertEquals("Server description", media.alt)

        val originalOnly = NostrMedia()
        MediaResourceImpl.applyNip94Tags(
            originalOnly,
            listOf(listOf("ox", "original-hash")),
        )
        assertEquals(null, originalOnly.sha256)
    }
}
