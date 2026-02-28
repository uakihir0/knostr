package work.socialhub.knostr.social

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MuteResourceTest : AbstractTest() {

    @Test
    fun testMuteAndUnmute() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val targetPubkey = "aaaa1111bbbb2222cccc3333dddd4444eeee5555ffff6666aaaa1111bbbb2222"

            // Mute
            val muteResponse = social.mutes().mute(targetPubkey)
            val muteEvent = muteResponse.data
            println("Mute event: ${muteEvent.id}")
            assertNotNull(muteEvent.id)
            assertTrue(muteEvent.kind == 10000)

            // Verify p-tag is in the published event
            val pTags = muteEvent.tags.filter { it.size >= 2 && it[0] == "p" }
            val mutedPubkeys = pTags.map { it[1] }
            println("Muted pubkeys in event: $mutedPubkeys")
            assertTrue(mutedPubkeys.contains(targetPubkey), "Mute event should contain target p-tag")

            // Unmute
            val unmuteResponse = social.mutes().unmute(targetPubkey)
            val unmuteEvent = unmuteResponse.data
            println("Unmute event: ${unmuteEvent.id}")
            assertNotNull(unmuteEvent.id)

            // Verify p-tag is removed from the published event
            val afterPTags = unmuteEvent.tags.filter { it.size >= 2 && it[0] == "p" }
            val afterPubkeys = afterPTags.map { it[1] }
            println("Muted pubkeys after unmute: $afterPubkeys")
            assertTrue(!afterPubkeys.contains(targetPubkey), "Unmute event should not contain target p-tag")
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testGetMuteList() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val response = social.mutes().getMuteList()
            val muteList = response.data

            println("Mute list: ${muteList.size} entries")
            muteList.forEach { pk -> println("  $pk") }
            // Just verify API works (may be empty for test key)
            assertNotNull(muteList)
        } finally {
            disconnectRelays(nostr, scope)
        }
    }
}
