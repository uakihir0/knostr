package work.socialhub.knostr.social

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedResourceSensitiveTest : AbstractTest() {

    @Test
    fun testPostWithSensitive() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val response = social.feed().post(
                content = "knostr test sensitive post #knostr",
                sensitive = true,
            )
            val event = response.data

            println("Posted sensitive event: ${event.id}")
            println("  sensitive tag: ${event.tags.filter { it.size >= 1 && it[0] == "sensitive" }}")

            assertNotNull(event.id)
            assertTrue(event.kind == 1)
            val sensitiveTags = event.tags.filter { it.size >= 1 && it[0] == "sensitive" }
            assertTrue(sensitiveTags.isNotEmpty(), "Event should have sensitive tag")
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testPostWithoutSensitive() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val response = social.feed().post("knostr test post without sensitive flag")
            val event = response.data

            println("Posted non-sensitive event: ${event.id}")

            assertNotNull(event.id)
            assertTrue(event.kind == 1)
            val sensitiveTags = event.tags.filter { it.size >= 1 && it[0] == "sensitive" }
            assertTrue(sensitiveTags.isEmpty(), "Event should not have sensitive tag")
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testReplyWithSensitive() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val parentResponse = social.feed().post("knostr test parent for sensitive reply")
            val parentEvent = parentResponse.data

            val replyResponse = social.feed().reply(
                content = "knostr test reply with sensitive flag",
                replyToEventId = parentEvent.id,
                sensitive = true,
            )
            val replyEvent = replyResponse.data

            println("Reply with sensitive: ${replyEvent.id}")

            val sensitiveTags = replyEvent.tags.filter { it.size >= 1 && it[0] == "sensitive" }
            assertTrue(sensitiveTags.isNotEmpty(), "Reply should have sensitive tag")
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testQuoteRepostWithSensitive() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val originalResponse = social.feed().post("knostr test original for sensitive quote repost")
            val originalEvent = originalResponse.data

            val quoteResponse = social.feed().quoteRepost(
                eventId = originalEvent.id,
                comment = "knostr test quote repost with sensitive",
                sensitive = true,
            )
            val quoteEvent = quoteResponse.data

            println("Quote repost with sensitive: ${quoteEvent.id}")

            val sensitiveTags = quoteEvent.tags.filter { it.size >= 1 && it[0] == "sensitive" }
            assertTrue(sensitiveTags.isNotEmpty(), "Quote repost should have sensitive tag")
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testContentWarningAndSensitiveTogether() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val response = social.feed().post(
                content = "knostr test post with both content warning and sensitive",
                contentWarning = "NSFW content",
                sensitive = true,
            )
            val event = response.data

            println("Posted event with both warning and sensitive: ${event.id}")

            val contentWarningTag = event.tags.find { it.size >= 2 && it[0] == "content-warning" }
            val sensitiveTags = event.tags.filter { it.size >= 1 && it[0] == "sensitive" }

            assertNotNull(contentWarningTag, "Event should have content-warning tag")
            assertTrue(contentWarningTag[1] == "NSFW content")
            assertTrue(sensitiveTags.isNotEmpty(), "Event should also have sensitive tag")
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testExpiryAndSensitiveTogether() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val futureTime = System.currentTimeMillis() / 1000 + 3600L
            val response = social.feed().post(
                content = "knostr test post with both expiry and sensitive",
                expiry = futureTime,
                sensitive = true,
            )
            val event = response.data

            println("Posted event with both expiry and sensitive: ${event.id}")

            val xTag = event.tags.find { it.size >= 2 && it[0] == "X" }
            val sensitiveTags = event.tags.filter { it.size >= 1 && it[0] == "sensitive" }

            assertNotNull(xTag, "Event should have X tag")
            assertTrue(xTag[1] == futureTime.toString())
            assertTrue(sensitiveTags.isNotEmpty(), "Event should have sensitive tag")
        } finally {
            disconnectRelays(nostr, scope)
        }
    }
}
