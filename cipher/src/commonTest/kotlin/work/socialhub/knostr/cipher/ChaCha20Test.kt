package work.socialhub.knostr.cipher

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ChaCha20 tests using RFC 8439 test vectors.
 */
class ChaCha20Test {

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

    // RFC 8439 §2.4.2 — ChaCha20 Encryption Test Vector
    @Test
    fun testEncryptionVector() {
        val key = hexToBytes(
            "000102030405060708090a0b0c0d0e0f" +
            "101112131415161718191a1b1c1d1e1f"
        )
        val nonce = hexToBytes("000000000000004a00000000")
        val counter = 1

        val plaintext = (
            "Ladies and Gentlemen of the class of '99: " +
            "If I could offer you only one tip for the future, sunscreen would be it."
        ).encodeToByteArray()

        val expectedCiphertext = hexToBytes(
            "6e2e359a2568f98041ba0728dd0d6981" +
            "e97e7aec1d4360c20a27afccfd9fae0b" +
            "f91b65c5524733ab8f593dabcd62b357" +
            "1639d624e65152ab8f530c359f0861d8" +
            "07ca0dbf500d6a6156a38e088a22b65e" +
            "52bc514d16ccf806818ce91ab7793736" +
            "5af90bbf74a35be6b40b8eedf2785e42" +
            "874d"
        )

        val ciphertext = ChaCha20.encrypt(plaintext, key, nonce, counter)
        assertEquals(bytesToHex(expectedCiphertext), bytesToHex(ciphertext))
    }

    // Verify encryption == decryption (XOR is its own inverse)
    @Test
    fun testEncryptDecryptRoundTrip() {
        val key = hexToBytes(
            "000102030405060708090a0b0c0d0e0f" +
            "101112131415161718191a1b1c1d1e1f"
        )
        val nonce = hexToBytes("000000000000004a00000000")

        val plaintext = "Hello, World! This is a test of ChaCha20.".encodeToByteArray()

        val ciphertext = ChaCha20.encrypt(plaintext, key, nonce)
        val decrypted = ChaCha20.encrypt(ciphertext, key, nonce)

        assertEquals(bytesToHex(plaintext), bytesToHex(decrypted))
    }

    // RFC 8439 §2.3.2 — ChaCha20 Block Function Test Vector (encrypt zeros to get keystream)
    @Test
    fun testKeystreamVector() {
        val key = hexToBytes(
            "000102030405060708090a0b0c0d0e0f" +
            "101112131415161718191a1b1c1d1e1f"
        )
        val nonce = hexToBytes("000000090000004a00000000")
        val counter = 1

        // Encrypt 64 zero bytes to get the keystream
        val zeros = ByteArray(64)
        val keystream = ChaCha20.encrypt(zeros, key, nonce, counter)

        val expectedKeystream = hexToBytes(
            "10f1e7e4d13b5915500fdd1fa32071c4" +
            "c7d1f4c733c068030422aa9ac3d46c4e" +
            "d2826446079faa0914c2d705d98b02a2" +
            "b5129cd1de164eb9cbd083e8a2503c4e"
        )

        assertEquals(bytesToHex(expectedKeystream), bytesToHex(keystream))
    }
}
