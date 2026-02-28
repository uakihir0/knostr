package work.socialhub.knostr

import work.socialhub.knostr.util.Hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals

class HexTest {

    @Test
    fun testEncode() {
        val bytes = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertEquals("deadbeef", Hex.encode(bytes))
    }

    @Test
    fun testDecode() {
        val expected = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertContentEquals(expected, Hex.decode("deadbeef"))
    }

    @Test
    fun testRoundTrip() {
        val original = byteArrayOf(0x00, 0x01, 0x7F, 0x80.toByte(), 0xFF.toByte())
        val encoded = Hex.encode(original)
        val decoded = Hex.decode(encoded)
        assertContentEquals(original, decoded)
    }

    @Test
    fun testEmptyArray() {
        assertEquals("", Hex.encode(byteArrayOf()))
        assertContentEquals(byteArrayOf(), Hex.decode(""))
    }
}
