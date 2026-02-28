package work.socialhub.knostr.social

import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.social.internal.SocialMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SocialMapperTest {

    private val testPubkey = "6e468422dfb74a5738702a8823b9b28168abab8655faacb6853cd0ee15deee93"
    // Valid 64-char hex event IDs for tests
    private val eventId1 = "4376c65d2f232afbe9b882a35baa4f6fe8667c4e684749af565f981833ed6a65"
    private val eventId2 = "3da979448d9ba263864c4d6f14984c423a3838364ec255f03c7904b1ae77f206"
    private val eventId3 = "bf2376e17ba4ec269d10fcc996a4746b451152be9031fa48e74553dde5526bce"
    private val testSig = "908a15e46fb4d8675bab026fc230a0e3542bfade63da02d542fb78b2a8513fcd0092619a2c8c1221e581946e0191f2af505dfdf8657a414dbca329186f009262"

    @Test
    fun testToUser_fromMetadataEvent() {
        val event = NostrEvent(
            id = eventId1,
            pubkey = testPubkey,
            createdAt = 1000,
            kind = 0,
            tags = listOf(),
            content = """{"name":"alice","display_name":"Alice","about":"Hello world","picture":"https://example.com/pic.jpg","nip05":"alice@example.com"}""",
            sig = testSig,
        )
        val user = SocialMapper.toUser(event)
        assertEquals(testPubkey, user.pubkey)
        assertEquals("alice", user.name)
        assertEquals("Alice", user.displayName)
        assertEquals("Hello world", user.about)
        assertEquals("https://example.com/pic.jpg", user.picture)
        assertEquals("alice@example.com", user.nip05)
        assertTrue(user.npub.startsWith("npub"))
    }

    @Test
    fun testToUser_emptyContent() {
        val event = NostrEvent(
            id = eventId1,
            pubkey = testPubkey,
            createdAt = 1000,
            kind = 0,
            tags = listOf(),
            content = "",
            sig = testSig,
        )
        val user = SocialMapper.toUser(event)
        assertEquals(testPubkey, user.pubkey)
        assertNull(user.name)
        assertNull(user.displayName)
    }

    @Test
    fun testToNote_simple() {
        val event = NostrEvent(
            id = eventId1,
            pubkey = testPubkey,
            createdAt = 2000,
            kind = 1,
            tags = listOf(),
            content = "Hello nostr!",
            sig = testSig,
        )
        val note = SocialMapper.toNote(event)
        assertEquals("Hello nostr!", note.content)
        assertEquals(2000L, note.createdAt)
        assertNull(note.rootEventId)
        assertNull(note.replyToEventId)
        assertTrue(note.noteId.startsWith("note"))
    }

    @Test
    fun testToNote_nip10MarkedTags() {
        val event = NostrEvent(
            id = eventId1,
            pubkey = testPubkey,
            createdAt = 2000,
            kind = 1,
            tags = listOf(
                listOf("e", "root_event_id", "", "root"),
                listOf("e", "reply_event_id", "", "reply"),
            ),
            content = "Replying to thread",
            sig = testSig,
        )
        val note = SocialMapper.toNote(event)
        assertEquals("root_event_id", note.rootEventId)
        assertEquals("reply_event_id", note.replyToEventId)
    }

    @Test
    fun testToNote_nip10LegacyTags() {
        // Legacy: first e-tag = root, last e-tag = reply
        val event = NostrEvent(
            id = eventId1,
            pubkey = testPubkey,
            createdAt = 2000,
            kind = 1,
            tags = listOf(
                listOf("e", "first_event_id"),
                listOf("e", "second_event_id"),
                listOf("e", "third_event_id"),
            ),
            content = "Legacy reply",
            sig = testSig,
        )
        val note = SocialMapper.toNote(event)
        assertEquals("first_event_id", note.rootEventId)
        assertEquals("third_event_id", note.replyToEventId)
    }

    @Test
    fun testToReaction_like() {
        val event = NostrEvent(
            id = eventId2,
            pubkey = testPubkey,
            createdAt = 3000,
            kind = 7,
            tags = listOf(
                listOf("e", "target_event_id"),
                listOf("p", "target_pubkey"),
            ),
            content = "+",
            sig = testSig,
        )
        val reaction = SocialMapper.toReaction(event)
        assertEquals("+", reaction.content)
        assertEquals("target_event_id", reaction.targetEventId)
        assertEquals(3000L, reaction.createdAt)
    }

    @Test
    fun testToReaction_emoji() {
        val event = NostrEvent(
            id = eventId2,
            pubkey = testPubkey,
            createdAt = 3000,
            kind = 7,
            tags = listOf(
                listOf("e", "target_event_id"),
            ),
            content = "\uD83D\uDE80",
            sig = testSig,
        )
        val reaction = SocialMapper.toReaction(event)
        assertEquals("\uD83D\uDE80", reaction.content)
    }

    @Test
    fun testToFollowList() {
        val event = NostrEvent(
            id = eventId3,
            pubkey = testPubkey,
            createdAt = 4000,
            kind = 3,
            tags = listOf(
                listOf("p", "pubkey1", "wss://relay1.example.com"),
                listOf("p", "pubkey2"),
                listOf("p", "pubkey3"),
            ),
            content = "",
            sig = testSig,
        )
        val followList = SocialMapper.toFollowList(event)
        assertEquals(3, followList.size)
        assertEquals("pubkey1", followList[0])
        assertEquals("pubkey2", followList[1])
        assertEquals("pubkey3", followList[2])
    }

    @Test
    fun testToFollowList_emptyTags() {
        val event = NostrEvent(
            id = eventId3,
            pubkey = testPubkey,
            createdAt = 4000,
            kind = 3,
            tags = listOf(),
            content = "",
            sig = testSig,
        )
        val followList = SocialMapper.toFollowList(event)
        assertTrue(followList.isEmpty())
    }
}
