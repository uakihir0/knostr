package work.socialhub.knostr.social

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MediaResourceTest : AbstractTest() {

    @Test
    fun testGetServerInfo() = runBlocking {
        val social = social()

        val response = social.media().getServerInfo("https://nostr.build")
        val apiUrl = response.data

        println("NIP-96 API URL: $apiUrl")
        assertTrue(apiUrl.isNotEmpty())
        assertTrue(apiUrl.startsWith("https://"))
    }

    @Test
    fun testUploadImage() = runBlocking {
        val social = social()

        // Load test image from resources
        val imageBytes = this::class.java.getResourceAsStream("/image/200x200.png")!!
            .readBytes()
        println("Image size: ${imageBytes.size} bytes")

        val response = social.media().upload(
            serverUrl = "https://nostr.build",
            fileData = imageBytes,
            fileName = "200x200.png",
            mimeType = "image/png",
            description = "knostr upload test",
        )
        val media = response.data

        println("Uploaded URL: ${media.url}")
        println("  mimeType: ${media.mimeType}")
        println("  dimensions: ${media.width}x${media.height}")
        println("  size: ${media.sizeBytes}")
        println("  blurhash: ${media.blurhash}")
        println("  sha256: ${media.sha256}")

        assertNotNull(media.url)
        assertTrue(media.url.isNotEmpty(), "Upload URL should not be empty")
    }

    @Test
    fun testUploadAndPost() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            // 1. Upload image to NIP-96 server
            val imageBytes = this::class.java.getResourceAsStream("/image/200x200.png")!!
                .readBytes()

            val uploadResponse = social.media().upload(
                serverUrl = "https://nostr.build",
                fileData = imageBytes,
                fileName = "200x200.png",
                mimeType = "image/png",
                description = "knostr image post test",
            )
            val media = uploadResponse.data
            println("Uploaded: ${media.url}")
            assertTrue(media.url.isNotEmpty())

            // 2. Post a note with the image URL and imeta tag
            val imetaTag = mutableListOf("imeta", "url ${media.url}")
            media.mimeType.let { imetaTag.add("m $it") }
            media.blurhash?.let { imetaTag.add("blurhash $it") }
            if (media.width != null && media.height != null) {
                imetaTag.add("dim ${media.width}x${media.height}")
            }
            media.sha256?.let { imetaTag.add("x $it") }

            val postResponse = social.feed().post(
                content = "knostr image post test ${media.url}",
                tags = listOf(imetaTag),
            )
            val event = postResponse.data

            println("Posted note: ${event.id}")
            println("  content: ${event.content}")
            println("  tags: ${event.tags}")

            assertNotNull(event.id)
            assertTrue(event.content.contains(media.url))

            // Verify imeta tag
            val imetaTags = event.tags.filter { it.isNotEmpty() && it[0] == "imeta" }
            assertTrue(imetaTags.isNotEmpty(), "Should have imeta tag")
            assertTrue(imetaTags[0].any { it.startsWith("url ") }, "imeta should contain url")
        } finally {
            disconnectRelays(nostr, scope)
        }
    }
}
