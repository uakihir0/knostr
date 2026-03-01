package work.socialhub.knostr.cipher

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AES-256 and AES-256-CBC tests using NIST test vectors.
 */
class AesTest {

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

    // NIST AES-256 ECB test vector (FIPS 197 Appendix C.3)
    @Test
    fun testEncryptBlock() {
        val key = hexToBytes("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val plaintext = hexToBytes("00112233445566778899aabbccddeeff")
        val expectedCiphertext = "8ea2b7ca516745bfeafc49904b496089"

        val ciphertext = Aes.encryptBlock(plaintext, key)
        assertEquals(expectedCiphertext, bytesToHex(ciphertext))
    }

    @Test
    fun testDecryptBlock() {
        val key = hexToBytes("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val ciphertext = hexToBytes("8ea2b7ca516745bfeafc49904b496089")
        val expectedPlaintext = "00112233445566778899aabbccddeeff"

        val plaintext = Aes.decryptBlock(ciphertext, key)
        assertEquals(expectedPlaintext, bytesToHex(plaintext))
    }

    @Test
    fun testEncryptDecryptRoundTrip() {
        val key = hexToBytes("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val plaintext = hexToBytes("00112233445566778899aabbccddeeff")

        val ciphertext = Aes.encryptBlock(plaintext, key)
        val decrypted = Aes.decryptBlock(ciphertext, key)
        assertEquals(bytesToHex(plaintext), bytesToHex(decrypted))
    }

    // --- AES-256-CBC tests ---

    @Test
    fun testCbcEncryptDecryptRoundTrip() {
        val key = hexToBytes("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val iv = hexToBytes("000102030405060708090a0b0c0d0e0f")
        val plaintext = "Hello, AES-256-CBC!".encodeToByteArray()

        val ciphertext = AesCbc.encrypt(plaintext, key, iv)
        val decrypted = AesCbc.decrypt(ciphertext, key, iv)

        assertEquals(bytesToHex(plaintext), bytesToHex(decrypted))
    }

    @Test
    fun testCbcWithExactBlockSize() {
        val key = hexToBytes("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val iv = hexToBytes("000102030405060708090a0b0c0d0e0f")
        // Exactly 16 bytes — PKCS7 will add a full padding block
        val plaintext = "0123456789abcdef".encodeToByteArray()

        val ciphertext = AesCbc.encrypt(plaintext, key, iv)
        assertEquals(32, ciphertext.size) // 16 + 16 padding block

        val decrypted = AesCbc.decrypt(ciphertext, key, iv)
        assertEquals(bytesToHex(plaintext), bytesToHex(decrypted))
    }

    @Test
    fun testCbcWithMultipleBlocks() {
        val key = hexToBytes("603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4")
        val iv = hexToBytes("000102030405060708090a0b0c0d0e0f")
        val plaintext = "This is a longer message that spans multiple AES blocks for testing.".encodeToByteArray()

        val ciphertext = AesCbc.encrypt(plaintext, key, iv)
        val decrypted = AesCbc.decrypt(ciphertext, key, iv)

        assertEquals(String(plaintext), String(decrypted))
    }

    // NIST AES-256-CBC test vector (SP 800-38A F.2.5/F.2.6)
    @Test
    fun testCbcNistVector() {
        val key = hexToBytes("603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4")
        val iv = hexToBytes("000102030405060708090a0b0c0d0e0f")

        // 4 blocks of plaintext (no PKCS7 padding in this test, we test raw CBC)
        val block1 = hexToBytes("6bc1bee22e409f96e93d7e117393172a")
        val block2 = hexToBytes("ae2d8a571e03ac9c9eb76fac45af8e51")
        val block3 = hexToBytes("30c81c46a35ce411e5fbc1191a0a52ef")
        val block4 = hexToBytes("f69f2445df4f9b17ad2b417be66c3710")

        // Expected ciphertext blocks
        val expected1 = "f58c4c04d6e5f1ba779eabfb5f7bfbd6"
        val expected2 = "9cfc4e967edb808d679f777bc6702c7d"
        val expected3 = "39f23369a9d9bacfa530e26304231461"
        val expected4 = "b2eb05e2c39be9fcda6c19078c6a9d1b"

        // Encrypt all 4 blocks (64 bytes total, AesCbc adds PKCS7 padding)
        val plaintext = block1 + block2 + block3 + block4
        val ciphertext = AesCbc.encrypt(plaintext, key, iv)

        // First 64 bytes should match NIST expected output
        assertEquals(expected1, bytesToHex(ciphertext.copyOfRange(0, 16)))
        assertEquals(expected2, bytesToHex(ciphertext.copyOfRange(16, 32)))
        assertEquals(expected3, bytesToHex(ciphertext.copyOfRange(32, 48)))
        assertEquals(expected4, bytesToHex(ciphertext.copyOfRange(48, 64)))

        // Decrypt should recover original plaintext
        val decrypted = AesCbc.decrypt(ciphertext, key, iv)
        assertEquals(bytesToHex(plaintext), bytesToHex(decrypted))
    }
}
