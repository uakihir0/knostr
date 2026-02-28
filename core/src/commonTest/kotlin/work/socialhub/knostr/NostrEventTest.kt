package work.socialhub.knostr

import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.internal.InternalUtility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NostrEventTest {

    private val sampleEvent = NostrEvent(
        id = "4376c65d2f232afbe9b882a35baa4f6fe8667c4e684749af565f981833ed6a65",
        pubkey = "6e468422dfb74a5738702a8823b9b28168abab8655faacb6853cd0ee15deee93",
        createdAt = 1673347337,
        kind = 1,
        tags = listOf(
            listOf("e", "3da979448d9ba263864c4d6f14984c423a3838364ec255f03c7904b1ae77f206"),
            listOf("p", "bf2376e17ba4ec269d10fcc996a4746b451152be9031fa48e74553dde5526bce"),
        ),
        content = "Walled gardens became prisons, and nostr is the first step towards tearing down the walls.",
        sig = "908a15e46fb4d8675bab026fc230a0e3542bfade63da02d542fb78b2a8513fcd0092619a2c8c1221e581946e0191f2af505dfdf8657a414dbca329186f009262",
    )

    @Test
    fun testSerialize() {
        val json = InternalUtility.toJson(sampleEvent)
        // Verify created_at field name is used (SerialName)
        assertTrue(json.contains("\"created_at\""))
        assertTrue(json.contains("1673347337"))
        assertTrue(json.contains("\"kind\":1"))
    }

    @Test
    fun testDeserialize() {
        val json = InternalUtility.toJson(sampleEvent)
        val parsed = InternalUtility.fromJson<NostrEvent>(json)
        assertEquals(sampleEvent.id, parsed.id)
        assertEquals(sampleEvent.pubkey, parsed.pubkey)
        assertEquals(sampleEvent.createdAt, parsed.createdAt)
        assertEquals(sampleEvent.kind, parsed.kind)
        assertEquals(sampleEvent.content, parsed.content)
        assertEquals(sampleEvent.sig, parsed.sig)
        assertEquals(sampleEvent.tags.size, parsed.tags.size)
    }

    @Test
    fun testRoundTrip() {
        val json = InternalUtility.toJson(sampleEvent)
        val parsed = InternalUtility.fromJson<NostrEvent>(json)
        assertEquals(sampleEvent, parsed)
    }
}
