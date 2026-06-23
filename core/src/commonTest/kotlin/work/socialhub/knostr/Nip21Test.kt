package work.socialhub.knostr

import work.socialhub.knostr.internal.NipResourceImpl
import work.socialhub.knostr.util.Nip21
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Nip21Test {

    private val nip = NipResourceImpl(NostrConfig())
    private val eventId = "4376c65d2f232afbe9b882a35baa4f6fe8667c4e684749af565f981833ed6a65"
    private val eventId2 = "3da979448d9ba263864c4d6f14984c423a3838364ec255f03c7904b1ae77f206"
    private val pubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"

    @Test
    fun testExtractNote() {
        val note = nip.encodeNote(eventId)
        val content = "Check this out nostr:$note"
        assertEquals(listOf(eventId), Nip21.extractEventIds(content))
    }

    @Test
    fun testExtractNevent() {
        val nevent = nip.encodeNevent(eventId, listOf("wss://relay.damus.io"), pubkey)
        val content = "Quoting nostr:$nevent for context"
        assertEquals(listOf(eventId), Nip21.extractEventIds(content))
    }

    @Test
    fun testFirstEventId() {
        val nevent = nip.encodeNevent(eventId)
        val note = nip.encodeNote(eventId2)
        val content = "first nostr:$nevent then nostr:$note"
        assertEquals(eventId, Nip21.firstEventId(content))
    }

    @Test
    fun testExtractMultiple() {
        val note = nip.encodeNote(eventId)
        val nevent = nip.encodeNevent(eventId2)
        val content = "a nostr:$note b nostr:$nevent c"
        assertEquals(listOf(eventId, eventId2), Nip21.extractEventIds(content))
    }

    @Test
    fun testNoReference() {
        assertTrue(Nip21.extractEventIds("just a plain note with no quote").isEmpty())
        assertNull(Nip21.firstEventId("plain"))
    }

    @Test
    fun testIgnoresNpubAndNprofile() {
        // npub / nprofile references are not event references and must be ignored.
        val npub = nip.encodeNpub(pubkey)
        val nprofile = nip.encodeNprofile(pubkey)
        val content = "mentioning nostr:$npub and nostr:$nprofile"
        assertTrue(Nip21.extractEventIds(content).isEmpty())
    }

    @Test
    fun testIgnoresInvalidToken() {
        // Malformed bech32 (bad checksum) must be skipped, not throw.
        val content = "nostr:note1invalidchecksumxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
        assertTrue(Nip21.extractEventIds(content).isEmpty())
    }
}
