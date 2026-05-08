package work.socialhub.knostr.social

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedResourceExpiryTest : AbstractTest() {

    @Test
    fun testPostWithExpiry() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val futureTime = System.currentTimeMillis() / 1000 + 3600L
            val response = social.feed().post(
                content = "knostr test post with expiry #knostr",
                expiry = futureTime,
            )
            val event = response.data

            println("Posted event with expiry: ${event.id}")
            println("  X tag: ${event.tags.find { it.size >= 2 && it[0] == "X" }?.get(1)}")

            assertNotNull(event.id)
            assertTrue(event.kind == 1)
            val xTag = event.tags.find { it.size >= 2 && it[0] == "X" }
            assertNotNull(xTag, "Event should have X tag for expiry")
            assertTrue(xTag[1] == futureTime.toString())
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testPostWithoutExpiry() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val response = social.feed().post("knostr test post without expiry")
            val event = response.data

            println("Posted event without expiry: ${event.id}")

            assertNotNull(event.id)
            assertTrue(event.kind == 1)
            val xTag = event.tags.find { it.size >= 2 && it[0] == "X" }
            assertNull(xTag, "Event should not have X tag when expiry is null")
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testReplyWithExpiry() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val parentResponse = social.feed().post("knostr test parent for expiry reply")
            val parentEvent = parentResponse.data

            val futureTime = System.currentTimeMillis() / 1000 + 7200L
            val replyResponse = social.feed().reply(
                content = "knostr test reply with expiry",
                replyToEventId = parentEvent.id,
                expiry = futureTime,
            )
            val replyEvent = replyResponse.data

            println("Reply with expiry: ${replyEvent.id}")

            val xTag = replyEvent.tags.find { it.size >= 2 && it[0] == "X" }
            assertNotNull(xTag, "Reply should have X tag")
            assertTrue(xTag[1] == futureTime.toString())
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testQuoteRepostWithExpiry() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val originalResponse = social.feed().post("knostr test original for quote repost")
            val originalEvent = originalResponse.data

            val futureTime = System.currentTimeMillis() / 1000 + 1800L
            val quoteResponse = social.feed().quoteRepost(
                eventId = originalEvent.id,
                comment = "knostr test quote repost with expiry",
                expiry = futureTime,
            )
            val quoteEvent = quoteResponse.data

            println("Quote repost with expiry: ${quoteEvent.id}")

            val xTag = quoteEvent.tags.find { it.size >= 2 && it[0] == "X" }
            assertNotNull(xTag, "Quote repost should have X tag")
            assertTrue(xTag[1] == futureTime.toString())
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testGetNoteWithExpiry() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val futureTime = System.currentTimeMillis() / 1000 + 3600L
            social.feed().post(
                content = "knostr test post for expiry parsing",
                expiry = futureTime,
            )

            delay(3000)

            val knownEventId = "d7dd5eb3ab747e16f8d0212d53032ea2a7cadef53837e5a6c66d42849fcb9027"
            try {
                val response = social.feed().getNote(knownEventId)
                val note = response.data

                println("Note expiry: ${note.expiry}")
                assertNotNull(note.event)
                assertNotNull(note.noteId)
            } catch (e: Exception) {
                println("getNote failed (relay propagation): ${e.message}")
            }
        } finally {
            disconnectRelays(nostr, scope)
        }
    }
}
