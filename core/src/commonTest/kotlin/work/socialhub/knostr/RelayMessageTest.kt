package work.socialhub.knostr

import work.socialhub.knostr.internal.InternalUtility
import work.socialhub.knostr.relay.RelayMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RelayMessageTest {

    @Test
    fun testParseEventMsg() {
        val json = """["EVENT","sub1",{"id":"abc","pubkey":"def","created_at":1000,"kind":1,"tags":[],"content":"hello","sig":"sig1"}]"""
        val msg = InternalUtility.parseRelayMessage(json)
        assertIs<RelayMessage.EventMsg>(msg)
        assertEquals("sub1", msg.subscriptionId)
        assertEquals("abc", msg.event.id)
        assertEquals("hello", msg.event.content)
    }

    @Test
    fun testParseOkMsg_success() {
        val json = """["OK","eventid123",true,""]"""
        val msg = InternalUtility.parseRelayMessage(json)
        assertIs<RelayMessage.OkMsg>(msg)
        assertEquals("eventid123", msg.eventId)
        assertTrue(msg.success)
        assertEquals("", msg.message)
    }

    @Test
    fun testParseOkMsg_failure() {
        val json = """["OK","eventid123",false,"duplicate: already have this event"]"""
        val msg = InternalUtility.parseRelayMessage(json)
        assertIs<RelayMessage.OkMsg>(msg)
        assertEquals("eventid123", msg.eventId)
        assertEquals(false, msg.success)
        assertEquals("duplicate: already have this event", msg.message)
    }

    @Test
    fun testParseEoseMsg() {
        val json = """["EOSE","sub123"]"""
        val msg = InternalUtility.parseRelayMessage(json)
        assertIs<RelayMessage.EoseMsg>(msg)
        assertEquals("sub123", msg.subscriptionId)
    }

    @Test
    fun testParseClosedMsg() {
        val json = """["CLOSED","sub123","rate-limited"]"""
        val msg = InternalUtility.parseRelayMessage(json)
        assertIs<RelayMessage.ClosedMsg>(msg)
        assertEquals("sub123", msg.subscriptionId)
        assertEquals("rate-limited", msg.message)
    }

    @Test
    fun testParseNoticeMsg() {
        val json = """["NOTICE","server is shutting down"]"""
        val msg = InternalUtility.parseRelayMessage(json)
        assertIs<RelayMessage.NoticeMsg>(msg)
        assertEquals("server is shutting down", msg.message)
    }
}
