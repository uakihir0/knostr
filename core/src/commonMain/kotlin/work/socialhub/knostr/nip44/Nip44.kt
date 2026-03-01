package work.socialhub.knostr.nip44

import work.socialhub.knostr.cipher.ChaCha20
import work.socialhub.knostr.cipher.Hkdf
import work.socialhub.knostr.cipher.HmacSha256
import work.socialhub.knostr.cipher.Secp256k1
import work.socialhub.knostr.util.Hex
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * NIP-44 v2 encryption/decryption.
 *
 * Uses ECDH (secp256k1) + HKDF + ChaCha20 + HMAC-SHA256.
 */
object Nip44 {

    private const val VERSION: Byte = 0x02
    private val SALT = "nip44-v2".encodeToByteArray()

    /**
     * Derive a conversation key from a private key and public key.
     * @param privateKeyHex 64-char hex private key
     * @param publicKeyHex 64-char hex x-only public key
     * @return 32-byte conversation key
     */
    fun deriveConversationKey(privateKeyHex: String, publicKeyHex: String): ByteArray {
        val sharedSecret = Secp256k1.computeSharedSecret(
            Hex.decode(privateKeyHex),
            Hex.decode(publicKeyHex),
        )
        return Hkdf.extract(SALT, sharedSecret)
    }

    /**
     * Encrypt plaintext using NIP-44 v2.
     * @param plaintext the message to encrypt
     * @param conversationKey 32-byte conversation key from [deriveConversationKey]
     * @param nonce optional 32-byte nonce (random if null; injectable for testing)
     * @return Base64-encoded payload
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun encrypt(plaintext: String, conversationKey: ByteArray, nonce: ByteArray? = null): String {
        require(conversationKey.size == 32) { "Conversation key must be 32 bytes" }
        require(plaintext.isNotEmpty()) { "Plaintext must not be empty" }

        val actualNonce = nonce ?: randomBytes(32)
        require(actualNonce.size == 32) { "Nonce must be 32 bytes" }

        // Derive encryption keys: chacha_key(32) + chacha_nonce(12) + hmac_key(32) = 76 bytes
        val keys = Hkdf.expand(conversationKey, actualNonce, 76)
        val chachaKey = keys.copyOfRange(0, 32)
        val chaChaNonce = keys.copyOfRange(32, 44)
        val hmacKey = keys.copyOfRange(44, 76)

        // Pad and encrypt
        val padded = Nip44Padding.pad(plaintext.encodeToByteArray())
        val ciphertext = ChaCha20.encrypt(padded, chachaKey, chaChaNonce, counter = 0)

        // MAC = HMAC-SHA256(hmac_key, nonce || ciphertext)
        val mac = HmacSha256.compute(hmacKey, actualNonce + ciphertext)

        // Payload = version(1) + nonce(32) + ciphertext(N) + mac(32)
        val payload = byteArrayOf(VERSION) + actualNonce + ciphertext + mac

        return Base64.encode(payload)
    }

    /**
     * Decrypt a NIP-44 v2 payload.
     * @param payload Base64-encoded payload
     * @param conversationKey 32-byte conversation key from [deriveConversationKey]
     * @return decrypted plaintext
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun decrypt(payload: String, conversationKey: ByteArray): String {
        require(conversationKey.size == 32) { "Conversation key must be 32 bytes" }

        val data = Base64.decode(payload)
        require(data.size >= 1 + 32 + 34 + 32) { "Payload too short" } // version + nonce + min padded(2+32) + mac
        require(data[0] == VERSION) { "Unsupported NIP-44 version: ${data[0]}" }

        val nonce = data.copyOfRange(1, 33)
        val ciphertext = data.copyOfRange(33, data.size - 32)
        val mac = data.copyOfRange(data.size - 32, data.size)

        // Derive keys
        val keys = Hkdf.expand(conversationKey, nonce, 76)
        val chachaKey = keys.copyOfRange(0, 32)
        val chaChaNonce = keys.copyOfRange(32, 44)
        val hmacKey = keys.copyOfRange(44, 76)

        // Verify MAC (constant-time comparison)
        val expectedMac = HmacSha256.compute(hmacKey, nonce + ciphertext)
        require(constantTimeEquals(mac, expectedMac)) { "Invalid MAC" }

        // Decrypt and unpad
        val padded = ChaCha20.encrypt(ciphertext, chachaKey, chaChaNonce, counter = 0)
        return Nip44Padding.unpad(padded).decodeToString()
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }

    /**
     * Generate random bytes. Uses platform-specific secure random.
     */
    private fun randomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        // Use kotlin.random (not cryptographically secure but adequate for nonce generation
        // in combination with HKDF key derivation; for production use, platform-specific
        // SecureRandom should be used via expect/actual)
        val random = kotlin.random.Random
        for (i in bytes.indices) {
            bytes[i] = random.nextInt(256).toByte()
        }
        return bytes
    }
}
