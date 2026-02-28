package work.socialhub.knostr.social

import kotlinx.coroutines.runBlocking
import work.socialhub.knostr.entity.NostrProfile
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UserResourceTest : AbstractTest() {

    @Test
    fun testGetProfile() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val pubkey = publicKey()
            val response = social.users().getProfile(pubkey)
            val user = response.data

            println("Profile: pubkey=${user.pubkey}")
            println("  npub=${user.npub}")
            println("  name=${user.name}")
            println("  displayName=${user.displayName}")
            println("  about=${user.about}")

            assertNotNull(user.pubkey)
            assertTrue(user.npub.startsWith("npub1"))
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testGetFollowing() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val pubkey = publicKey()
            val response = social.users().getFollowing(pubkey)
            val following = response.data

            println("Following count: ${following.size}")
            following.take(5).forEach { pk ->
                println("  $pk")
            }
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testUpdateProfile() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val profile = NostrProfile(
                name = "knostr-test",
                about = "Test account for knostr SDK",
                displayName = "knostr Test",
            )
            val response = social.users().updateProfile(profile)
            val event = response.data

            println("Updated profile event: ${event.id}")
            assertNotNull(event.id)
            assertTrue(event.kind == 0)
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testVerifyNip05() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            // Use a well-known NIP-05 address
            val response = social.users().verifyNip05("_@uakihir0.com")
            println("NIP-05 verified: ${response.data}")
        } finally {
            disconnectRelays(nostr, scope)
        }
    }
}
