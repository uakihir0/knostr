package work.socialhub.knostr.social

import kotlinx.coroutines.runBlocking
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
}
