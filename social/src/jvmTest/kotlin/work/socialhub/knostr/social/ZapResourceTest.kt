package work.socialhub.knostr.social

import kotlinx.coroutines.runBlocking
import work.socialhub.knostr.EventKind
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ZapResourceTest : AbstractTest() {

    @Test
    fun testCreateZapRequest() = runBlocking {
        // This test only needs signing, no relay connection
        val social = social()
        val recipientPubkey = publicKey()
        val response = social.zaps().createZapRequest(
            recipientPubkey = recipientPubkey,
            amountMilliSats = 1000,
            relays = listOf("wss://relay.damus.io", "wss://nos.lol"),
            message = "Test zap from knostr",
        )
        val event = response.data

        println("Zap request event: ${event.id}")
        println("  kind: ${event.kind}")
        println("  content: ${event.content}")

        assertNotNull(event.id)
        assertTrue(event.kind == EventKind.ZAP_REQUEST)
        assertTrue(event.content == "Test zap from knostr")

        // Verify tags
        val pTags = event.tags.filter { it.size >= 2 && it[0] == "p" }
        assertTrue(pTags.isNotEmpty(), "Should have p-tag")
        assertTrue(pTags[0][1] == recipientPubkey)

        val amountTags = event.tags.filter { it.size >= 2 && it[0] == "amount" }
        assertTrue(amountTags.isNotEmpty(), "Should have amount tag")
        assertTrue(amountTags[0][1] == "1000")

        val relaysTags = event.tags.filter { it.size >= 2 && it[0] == "relays" }
        assertTrue(relaysTags.isNotEmpty(), "Should have relays tag")
    }

    @Test
    fun testCreateZapRequestForEvent() = runBlocking {
        // This test only needs signing, no relay connection
        val social = social()

        val response = social.zaps().createZapRequest(
            recipientPubkey = publicKey(),
            amountMilliSats = 21000,
            relays = listOf("wss://relay.damus.io"),
            message = "Zapping your note!",
            eventId = "aaaa1111bbbb2222cccc3333dddd4444eeee5555ffff6666aaaa1111bbbb2222",
        )
        val event = response.data

        println("Zap request for event: ${event.id}")
        assertNotNull(event.id)

        // Verify e-tag
        val eTags = event.tags.filter { it.size >= 2 && it[0] == "e" }
        assertTrue(eTags.isNotEmpty(), "Should have e-tag for event zap")
        assertTrue(eTags[0][1] == "aaaa1111bbbb2222cccc3333dddd4444eeee5555ffff6666aaaa1111bbbb2222")
    }

    @Test
    fun testGetZapsForUser() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            // Query zaps for jack (well-known Nostr user)
            val response = social.zaps().getZapsForUser(
                "82341f882b6eabcd2ba7f1ef90aad961cf074af15b9ef44a09f9d2a8fbfbe6a2",
                limit = 5,
            )
            val zaps = response.data

            println("Zaps for user: ${zaps.size}")
            zaps.forEach { zap ->
                println("  amount=${zap.amountMilliSats} msats, message=${zap.message}")
            }
        } finally {
            disconnectRelays(nostr, scope)
        }
    }
}
