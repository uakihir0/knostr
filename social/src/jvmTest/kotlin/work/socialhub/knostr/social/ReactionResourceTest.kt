package work.socialhub.knostr.social

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import work.socialhub.knostr.NostrException
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReactionResourceTest : AbstractTest() {

    @Test
    fun testLike() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            // Post a note first
            val postResponse = social.feed().post("knostr like target")
            val event = postResponse.data

            // Like it
            val likeResponse = social.reactions().like(event.id, event.pubkey)
            val likeEvent = likeResponse.data

            println("Like event: ${likeEvent.id}")
            assertNotNull(likeEvent.id)
            assertTrue(likeEvent.kind == 7)
            assertTrue(likeEvent.content == "+")
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testCustomReaction() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            // Post a note first
            val postResponse = social.feed().post("knostr custom reaction target")
            val event = postResponse.data

            // React with custom emoji
            val reactResponse = social.reactions().react(event.id, event.pubkey, "🤙")
            val reactEvent = reactResponse.data

            println("Reaction event: ${reactEvent.id}")
            assertNotNull(reactEvent.id)
            assertTrue(reactEvent.kind == 7)
            assertTrue(reactEvent.content == "🤙")
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testGetReactions() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            // Post and like a note
            val postResponse = social.feed().post("knostr get reactions target")
            val event = postResponse.data
            social.reactions().like(event.id, event.pubkey)

            // Query reactions
            val reactionsResponse = social.reactions().getReactions(event.id)
            val reactions = reactionsResponse.data

            println("Reactions count: ${reactions.size}")
            reactions.forEach { r ->
                println("  content=${r.content}, from=${r.event.pubkey}")
            }
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testLikeAndUnlike() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            // Post and like a note
            val postResponse = social.feed().post("knostr unlike target")
            val event = postResponse.data
            social.reactions().like(event.id, event.pubkey)
            delay(3000)

            // Unlike it — requires relay to return our reaction event
            try {
                val unlikeResponse = social.reactions().unlike(event.id)
                println("Unlike result: ${unlikeResponse.data}")
                assertTrue(unlikeResponse.data)
            } catch (e: NostrException) {
                // Relay may not store/return events from test key
                println("Unlike skipped (relay did not return reaction): ${e.message}")
            }
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testGetUserReactions() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            // Get reactions for own key (may be empty if relay doesn't store test key events)
            val pubkey = publicKey()
            val response = social.reactions().getUserReactions(pubkey, limit = 5)
            val reactions = response.data

            println("User reactions: ${reactions.size}")
            reactions.forEach { r ->
                println("  content=${r.content}, target=${r.targetEventId.take(12)}...")
            }
            // Just verify API returns a list (may be empty)
            assertNotNull(reactions)
        } finally {
            disconnectRelays(nostr, scope)
        }
    }
}
