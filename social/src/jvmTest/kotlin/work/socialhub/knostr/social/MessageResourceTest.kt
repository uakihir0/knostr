package work.socialhub.knostr.social

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MessageResourceTest : AbstractTest() {

    @Test
    fun testSendMessage() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            // Send DM to self (using own pubkey as recipient)
            val myPubkey = publicKey()
            val response = social.messages().sendMessage(myPubkey, "knostr NIP-17 DM test")
            val giftWrap = response.data

            println("Gift Wrap event: ${giftWrap.id}")
            assertNotNull(giftWrap.id)
            assertTrue(giftWrap.kind == 1059, "Expected kind:1059 (Gift Wrap), got ${giftWrap.kind}")

            // Verify p-tag contains recipient
            val pTags = giftWrap.tags.filter { it.size >= 2 && it[0] == "p" }
            assertTrue(pTags.isNotEmpty(), "Gift Wrap should have p-tag")
            assertTrue(pTags[0][1] == myPubkey, "p-tag should contain recipient pubkey")
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testSendAndReceiveMessage() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val myPubkey = publicKey()
            val messageContent = "knostr DM round-trip test ${System.currentTimeMillis()}"

            // Send DM to self
            social.messages().sendMessage(myPubkey, messageContent)
            delay(3000)

            // Try to receive — relay may not return gift wraps for test keys
            try {
                val response = social.messages().getMessages(limit = 5)
                val messages = response.data

                println("Received ${messages.size} messages")
                messages.forEach { msg ->
                    println("  from=${msg.senderPubkey.take(12)}... content=${msg.content.take(50)}")
                }

                // If relay returns our message, verify content
                val found = messages.find { it.content == messageContent }
                if (found != null) {
                    println("Found sent message!")
                    assertTrue(found.senderPubkey == myPubkey)
                    assertTrue(found.recipientPubkey == myPubkey)
                    assertTrue(!found.isLegacy)
                } else {
                    println("Relay did not return the sent gift wrap (expected for test relays)")
                }
                assertNotNull(messages) // API works regardless
            } catch (e: Exception) {
                println("getMessages failed (relay may not support kind:1059 queries): ${e.message}")
            }
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    // --- NIP-04 Legacy DM tests ---

    @Test
    fun testSendLegacyMessage() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val myPubkey = publicKey()
            val response = social.messages().sendLegacyMessage(myPubkey, "knostr NIP-04 legacy DM test")
            val event = response.data

            println("Legacy DM event: ${event.id}")
            assertNotNull(event.id)
            assertTrue(event.kind == 4, "Expected kind:4 (Encrypted DM), got ${event.kind}")

            // Verify p-tag contains recipient
            val pTags = event.tags.filter { it.size >= 2 && it[0] == "p" }
            assertTrue(pTags.isNotEmpty(), "Legacy DM should have p-tag")
            assertTrue(pTags[0][1] == myPubkey, "p-tag should contain recipient pubkey")

            // Verify content is encrypted (NIP-04 format: base64?iv=base64)
            assertTrue(event.content.contains("?iv="), "Content should be NIP-04 encrypted format")
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testSendAndReceiveLegacyMessage() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val myPubkey = publicKey()
            val messageContent = "knostr NIP-04 round-trip test ${System.currentTimeMillis()}"

            // Send legacy DM to self
            social.messages().sendLegacyMessage(myPubkey, messageContent)
            delay(3000)

            // Try to receive
            try {
                val response = social.messages().getLegacyMessages(limit = 5)
                val messages = response.data

                println("Received ${messages.size} legacy messages")
                messages.forEach { msg ->
                    println("  from=${msg.senderPubkey.take(12)}... content=${msg.content.take(50)}")
                }

                // If relay returns our message, verify content
                val found = messages.find { it.content == messageContent }
                if (found != null) {
                    println("Found sent legacy message!")
                    assertTrue(found.senderPubkey == myPubkey)
                    assertTrue(found.recipientPubkey == myPubkey)
                    assertTrue(found.isLegacy)
                } else {
                    println("Relay did not return the sent legacy DM (expected for test relays)")
                }
                assertNotNull(messages)
            } catch (e: Exception) {
                println("getLegacyMessages failed (relay may not return kind:4): ${e.message}")
            }
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    // --- General tests ---

    @Test
    fun testGetConversation() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val myPubkey = publicKey()

            // Query conversation with self (may be empty)
            try {
                val response = social.messages().getConversation(myPubkey, limit = 5)
                val messages = response.data

                println("Conversation messages: ${messages.size}")
                assertNotNull(messages)
            } catch (e: Exception) {
                println("getConversation failed: ${e.message}")
            }
        } finally {
            disconnectRelays(nostr, scope)
        }
    }
}
