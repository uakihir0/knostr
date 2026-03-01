package work.socialhub.knostr.cipher

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * HKDF tests using RFC 5869 test vectors (SHA-256).
 */
class HkdfTest {

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

    // RFC 5869 Test Case 1
    @Test
    fun testCase1() {
        val ikm = hexToBytes("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
        val salt = hexToBytes("000102030405060708090a0b0c")
        val info = hexToBytes("f0f1f2f3f4f5f6f7f8f9")
        val expectedPrk = "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5"
        val expectedOkm = "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"

        val prk = Hkdf.extract(salt, ikm)
        assertEquals(expectedPrk, bytesToHex(prk))

        val okm = Hkdf.expand(prk, info, 42)
        assertEquals(expectedOkm, bytesToHex(okm))
    }

    // RFC 5869 Test Case 2 (longer inputs)
    @Test
    fun testCase2() {
        val ikm = hexToBytes("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f")
        val salt = hexToBytes("606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9fa0a1a2a3a4a5a6a7a8a9aaabacadaeaf")
        val info = hexToBytes("b0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedfe0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff")
        val expectedPrk = "06a6b88c5853361a06104c9ceb35b45cef760014904671014a193f40c15fc244"
        val expectedOkm = "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9aca3db71cc30c58179ec3e87c14c01d5c1f3434f1d87"

        val prk = Hkdf.extract(salt, ikm)
        assertEquals(expectedPrk, bytesToHex(prk))

        val okm = Hkdf.expand(prk, info, 82)
        assertEquals(expectedOkm, bytesToHex(okm))
    }

    // RFC 5869 Test Case 3 (empty salt and info)
    @Test
    fun testCase3() {
        val ikm = hexToBytes("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
        val salt = ByteArray(0)
        val info = ByteArray(0)
        val expectedPrk = "19ef24a32c717b167f33a91d6f648bdf96596776afdb6377ac434c1c293ccb04"
        val expectedOkm = "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"

        val prk = Hkdf.extract(salt, ikm)
        assertEquals(expectedPrk, bytesToHex(prk))

        val okm = Hkdf.expand(prk, info, 42)
        assertEquals(expectedOkm, bytesToHex(okm))
    }
}
