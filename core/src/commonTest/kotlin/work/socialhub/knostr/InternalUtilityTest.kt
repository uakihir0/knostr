package work.socialhub.knostr

import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.internal.InternalUtility
import work.socialhub.knostr.relay.RelayMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InternalUtilityTest {

    @Test
    fun testFromJsonToJson_NostrEvent() {
        val event = NostrEvent(
            id = "test_id",
            pubkey = "test_pubkey",
            createdAt = 1234567890,
            kind = 1,
            tags = listOf(listOf("e", "ref1")),
            content = "test content",
            sig = "test_sig",
        )
        val json = InternalUtility.toJson(event)
        val parsed = InternalUtility.fromJson<NostrEvent>(json)
        assertEquals(event, parsed)
    }

    @Test
    fun testFromJsonToJson_NostrFilter() {
        val filter = NostrFilter(
            kinds = listOf(1),
            authors = listOf("author1"),
            limit = 10,
        )
        val json = InternalUtility.toJson(filter)
        val parsed = InternalUtility.fromJson<NostrFilter>(json)
        assertEquals(filter, parsed)
    }

    @Test
    fun testSerializeForId() {
        // NIP-01: [0, pubkey, created_at, kind, tags, content]
        val result = InternalUtility.serializeForId(
            pubkey = "pubkey123",
            createdAt = 1000,
            kind = 1,
            tags = listOf(listOf("e", "ref1")),
            content = "hello",
        )
        assertTrue(result.startsWith("[0,"))
        assertTrue(result.contains("\"pubkey123\""))
        assertTrue(result.contains("1000"))
        assertTrue(result.contains("\"hello\""))
        assertTrue(result.contains("[\"e\",\"ref1\"]"))
    }

    @Test
    fun testParseRelayMessage_EVENT() {
        val json = """["EVENT","sub1",{"id":"abc","pubkey":"def","created_at":1000,"kind":1,"tags":[],"content":"test","sig":"sig1"}]"""
        val msg = InternalUtility.parseRelayMessage(json)
        assertIs<RelayMessage.EventMsg>(msg)
    }

    @Test
    fun testParseRelayMessage_OK() {
        val json = """["OK","evtid",true,""]"""
        val msg = InternalUtility.parseRelayMessage(json)
        assertIs<RelayMessage.OkMsg>(msg)
    }

    @Test
    fun testParseRelayMessage_EOSE() {
        val json = """["EOSE","sub1"]"""
        val msg = InternalUtility.parseRelayMessage(json)
        assertIs<RelayMessage.EoseMsg>(msg)
    }

    @Test
    fun testParseRelayMessage_CLOSED() {
        val json = """["CLOSED","sub1","reason"]"""
        val msg = InternalUtility.parseRelayMessage(json)
        assertIs<RelayMessage.ClosedMsg>(msg)
    }

    @Test
    fun testParseRelayMessage_NOTICE() {
        val json = """["NOTICE","hello"]"""
        val msg = InternalUtility.parseRelayMessage(json)
        assertIs<RelayMessage.NoticeMsg>(msg)
    }

    @Test
    fun testParseRelayMessage_unknownType() {
        val json = """["UNKNOWN","data"]"""
        assertFailsWith<NostrException> {
            InternalUtility.parseRelayMessage(json)
        }
    }

    @Test
    fun testBuildEventMessage() {
        val event = NostrEvent(
            id = "id1",
            pubkey = "pk1",
            createdAt = 1000,
            kind = 1,
            tags = listOf(),
            content = "hello",
            sig = "sig1",
        )
        val message = InternalUtility.buildEventMessage(event)
        assertTrue(message.startsWith("[\"EVENT\","))
        assertTrue(message.contains("\"id1\""))
    }

    @Test
    fun testBuildCloseMessage() {
        val message = InternalUtility.buildCloseMessage("sub123")
        assertEquals("""["CLOSE","sub123"]""", message)
    }
}
