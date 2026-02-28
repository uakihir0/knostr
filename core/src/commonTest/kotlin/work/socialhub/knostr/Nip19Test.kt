package work.socialhub.knostr

import work.socialhub.knostr.entity.Nip19Entity
import work.socialhub.knostr.util.Bech32
import work.socialhub.knostr.util.Hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class Nip19Test {

    @Test
    fun testEncodeDecodeNpub() {
        val pubkeyHex = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
        val encoded = Bech32.encode("npub", Hex.decode(pubkeyHex))

        val (hrp, data) = Bech32.decode(encoded)
        assertEquals("npub", hrp)

        val entity = when (hrp) {
            "npub" -> Nip19Entity.NPub(Hex.encode(data))
            else -> throw IllegalStateException()
        }
        assertIs<Nip19Entity.NPub>(entity)
        assertEquals(pubkeyHex, entity.pubkey)
    }

    @Test
    fun testEncodeDecodeNsec() {
        val seckeyHex = "67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa"
        val encoded = Bech32.encode("nsec", Hex.decode(seckeyHex))

        val (hrp, data) = Bech32.decode(encoded)
        assertEquals("nsec", hrp)

        val entity = when (hrp) {
            "nsec" -> Nip19Entity.NSec(Hex.encode(data))
            else -> throw IllegalStateException()
        }
        assertIs<Nip19Entity.NSec>(entity)
        assertEquals(seckeyHex, entity.seckey)
    }

    @Test
    fun testEncodeDecodeNote() {
        val eventIdHex = "4376c65d2f232afbe9b882a35baa4f6fe8667c4e684749af565f981833ed6a65"
        val encoded = Bech32.encode("note", Hex.decode(eventIdHex))

        val (hrp, data) = Bech32.decode(encoded)
        assertEquals("note", hrp)

        val entity = when (hrp) {
            "note" -> Nip19Entity.Note(Hex.encode(data))
            else -> throw IllegalStateException()
        }
        assertIs<Nip19Entity.Note>(entity)
        assertEquals(eventIdHex, entity.eventId)
    }
}
