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
}
