package work.socialhub.knostr.social

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
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
}
