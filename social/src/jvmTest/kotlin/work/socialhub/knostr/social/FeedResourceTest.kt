package work.socialhub.knostr.social

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FeedResourceTest : AbstractTest() {

    @Test
    fun testPost() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val response = social.feed().post("Hello from knostr test! #knostr")
            val event = response.data

            println("Posted event: ${event.id}")
            println("  kind: ${event.kind}")
            println("  content: ${event.content}")

            assertNotNull(event.id)
            assertTrue(event.kind == 1)
            assertTrue(event.content.contains("knostr"))
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testPostAndReply() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            // Post a note
            val postResponse = social.feed().post("knostr test parent note")
            val parentEvent = postResponse.data
            println("Parent event: ${parentEvent.id}")

            // Reply to the note
            val replyResponse = social.feed().reply(
                content = "knostr test reply",
                replyToEventId = parentEvent.id,
            )
            val replyEvent = replyResponse.data
            println("Reply event: ${replyEvent.id}")

            assertNotNull(replyEvent.id)
            assertTrue(replyEvent.kind == 1)

            // Verify NIP-10 e-tags
            val eTags = replyEvent.tags.filter { it.size >= 2 && it[0] == "e" }
            assertTrue(eTags.isNotEmpty(), "Reply should have e-tags")
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testPostAndRepost() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            // Post a note
            val postResponse = social.feed().post("knostr test repost target")
            val originalEvent = postResponse.data

            // Repost it
            val repostResponse = social.feed().repost(originalEvent.id)
            val repostEvent = repostResponse.data

            println("Repost event: ${repostEvent.id}")
            assertNotNull(repostEvent.id)
            assertTrue(repostEvent.kind == 6)
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testPostAndDelete() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            // Post a note
            val postResponse = social.feed().post("knostr test delete target")
            val event = postResponse.data

            // Delete it
            val deleteResponse = social.feed().delete(event.id, "test cleanup")
            println("Delete result: ${deleteResponse.data}")
            assertTrue(deleteResponse.data)
        } finally {
            disconnectRelays(nostr, scope)
        }
    }
}
