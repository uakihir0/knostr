package work.socialhub.knostr

import work.socialhub.knostr.util.Bech32
import work.socialhub.knostr.util.Hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Bech32Test {

    private val testPubkeyHex = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"

    @Test
    fun testEncode_npub() {
        val encoded = Bech32.encode("npub", Hex.decode(testPubkeyHex))
        assertTrue(encoded.startsWith("npub1"))
        // Verify roundtrip
        val (hrp, data) = Bech32.decode(encoded)
        assertEquals("npub", hrp)
        assertEquals(testPubkeyHex, Hex.encode(data))
    }

    @Test
    fun testDecode_npub() {
        // Encode first to get a known-good npub, then decode
        val encoded = Bech32.encode("npub", Hex.decode(testPubkeyHex))
        val (hrp, data) = Bech32.decode(encoded)
        assertEquals("npub", hrp)
        assertEquals(testPubkeyHex, Hex.encode(data))
    }

    @Test
    fun testRoundTrip() {
        val originalHex = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"
        val encoded = Bech32.encode("npub", Hex.decode(originalHex))
        val (hrp, decoded) = Bech32.decode(encoded)
        assertEquals("npub", hrp)
        assertEquals(originalHex, Hex.encode(decoded))
    }

    @Test
    fun testInvalidChecksum() {
        val encoded = Bech32.encode("npub", Hex.decode(testPubkeyHex))
        // Corrupt the last character
        val lastChar = encoded.last()
        val replacement = if (lastChar == 'q') 'p' else 'q'
        val corrupted = encoded.dropLast(1) + replacement
        assertFailsWith<IllegalArgumentException> {
            Bech32.decode(corrupted)
        }
    }
}
