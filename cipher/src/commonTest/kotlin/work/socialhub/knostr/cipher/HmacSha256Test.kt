package work.socialhub.knostr.cipher

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * HMAC-SHA256 tests using RFC 4231 test vectors.
 */
class HmacSha256Test {

    private fun hexToBytes(hex: String): ByteArray {
        val bytes = ByteArray(hex.length / 2)
        for (i in bytes.indices) {
            bytes[i] = ((hexDigit(hex[i * 2]) shl 4) or hexDigit(hex[i * 2 + 1])).toByte()
        }
        return bytes
    }

    private fun hexDigit(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> error("Invalid hex: $c")
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(hexChars[v ushr 4])
            sb.append(hexChars[v and 0x0F])
        }
        return sb.toString()
    }

    // RFC 4231 Test Case 1
    @Test
    fun testCase1() {
        val key = hexToBytes("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
        val data = "Hi There".encodeToByteArray()
        val expected = "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7"
        assertEquals(expected, bytesToHex(HmacSha256.compute(key, data)))
    }

    // RFC 4231 Test Case 2
    @Test
    fun testCase2() {
        val key = "Jefe".encodeToByteArray()
        val data = "what do ya want for nothing?".encodeToByteArray()
        val expected = "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843"
        assertEquals(expected, bytesToHex(HmacSha256.compute(key, data)))
    }

    // RFC 4231 Test Case 3
    @Test
    fun testCase3() {
        val key = hexToBytes("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        val data = hexToBytes("dd" + "dd".repeat(49))
        val expected = "773ea91e36800e46854db8ebd09181a72959098b3ef8c122d9635514ced565fe"
        assertEquals(expected, bytesToHex(HmacSha256.compute(key, data)))
    }

    // RFC 4231 Test Case 4
    @Test
    fun testCase4() {
        val key = hexToBytes("0102030405060708090a0b0c0d0e0f10111213141516171819")
        val data = hexToBytes("cd" + "cd".repeat(49))
        val expected = "82558a389a443c0ea4cc819899f2083a85f0faa3e578f8077a2e3ff46729665b"
        assertEquals(expected, bytesToHex(HmacSha256.compute(key, data)))
    }

    // RFC 4231 Test Case 6 (key longer than block size)
    @Test
    fun testCase6() {
        val key = hexToBytes("aa".repeat(131))
        val data = "Test Using Larger Than Block-Size Key - Hash Key First".encodeToByteArray()
        val expected = "60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54"
        assertEquals(expected, bytesToHex(HmacSha256.compute(key, data)))
    }

    // RFC 4231 Test Case 7 (key and data longer than block size)
    @Test
    fun testCase7() {
        val key = hexToBytes("aa".repeat(131))
        val data = "This is a test using a larger than block-size key and a larger than block-size data. The key needs to be hashed before being used by the HMAC algorithm.".encodeToByteArray()
        val expected = "9b09ffa71b942fcb27635fbcd5b0e944bfdc63644f0713938a7f51535c3a35e2"
        assertEquals(expected, bytesToHex(HmacSha256.compute(key, data)))
    }
}
