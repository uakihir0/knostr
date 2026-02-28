package work.socialhub.knostr.social

import kotlinx.coroutines.delay
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

    @Test
    fun testGetNote() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            // Query a well-known event: jack's "hello world" note on Nostr
            // (fiatjaf's first note - a widely replicated event)
            val knownEventId = "d7dd5eb3ab747e16f8d0212d53032ea2a7cadef53837e5a6c66d42849fcb9027"

            val noteResponse = social.feed().getNote(knownEventId)
            val note = noteResponse.data

            println("Got note: ${note.noteId}")
            println("  content: ${note.content}")
            assertNotNull(note.event)
            assertNotNull(note.noteId)
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testGetUserFeed() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            // Query fiatjaf's feed (well-known active Nostr user)
            val fiatjafPubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"

            val response = social.feed().getUserFeed(fiatjafPubkey, limit = 5)
            val notes = response.data

            println("User feed: ${notes.size} notes")
            notes.forEach { note ->
                println("  [${note.noteId.take(12)}...] ${note.content.take(60)}")
            }
            // Relay may return empty for some pubkeys; just verify API returns a list
            assertNotNull(notes)
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testGetMentions() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            // getMentions queries p-tag mentions for the authenticated user.
            // The test key may not have mentions, so we just verify the API works.
            val response = social.feed().getMentions(limit = 5)
            val notes = response.data

            println("Mentions: ${notes.size} notes")
            notes.forEach { note ->
                println("  [${note.noteId.take(12)}...] ${note.content.take(60)}")
            }
            // Just verify it returns a list (may be empty for test key)
            assertNotNull(notes)
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testGetThread() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            // Use a well-known event that has replies
            val knownEventId = "d7dd5eb3ab747e16f8d0212d53032ea2a7cadef53837e5a6c66d42849fcb9027"

            val threadResponse = social.feed().getThread(knownEventId)
            val thread = threadResponse.data

            println("Thread root: ${thread.rootNote?.noteId?.take(12)}...")
            println("Thread replies: ${thread.replies.size}")
            thread.replies.take(5).forEach { note ->
                println("  [${note.noteId.take(12)}...] ${note.content.take(60)}")
            }
            assertNotNull(thread.rootNote)
        } finally {
            disconnectRelays(nostr, scope)
        }
    }
}
