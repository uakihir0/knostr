package work.socialhub.knostr.nip04

import work.socialhub.knostr.cipher.AesCbc
import work.socialhub.knostr.cipher.Secp256k1
import work.socialhub.knostr.util.Hex
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * NIP-04 legacy encrypted DM implementation.
 *
 * Uses ECDH (secp256k1) + AES-256-CBC.
 * Format: base64(ciphertext)?iv=base64(iv)
 *
 * Note: NIP-04 is deprecated in favor of NIP-17 (Gift Wrap + NIP-44).
 * Metadata (sender/receiver) is visible in event tags.
 */
object Nip04 {

    /**
     * Compute shared secret for NIP-04 (ECDH x-coordinate, raw — no HKDF).
     */
    fun computeSharedSecret(privateKeyHex: String, publicKeyHex: String): ByteArray {
        return Secp256k1.computeSharedSecret(
            Hex.decode(privateKeyHex),
            Hex.decode(publicKeyHex),
        )
    }

    /**
     * Encrypt a message using NIP-04 format.
     * @param plaintext message to encrypt
     * @param sharedSecret 32-byte ECDH shared secret
     * @return NIP-04 formatted string: base64(ciphertext)?iv=base64(iv)
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun encrypt(plaintext: String, sharedSecret: ByteArray): String {
        require(sharedSecret.size == 32) { "Shared secret must be 32 bytes" }

        val iv = randomBytes(16)
        val ciphertext = AesCbc.encrypt(plaintext.encodeToByteArray(), sharedSecret, iv)

        return "${Base64.encode(ciphertext)}?iv=${Base64.encode(iv)}"
    }

    /**
     * Decrypt a NIP-04 formatted message.
     * @param payload NIP-04 formatted string: base64(ciphertext)?iv=base64(iv)
     * @param sharedSecret 32-byte ECDH shared secret
     * @return decrypted plaintext
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun decrypt(payload: String, sharedSecret: ByteArray): String {
        require(sharedSecret.size == 32) { "Shared secret must be 32 bytes" }

        val parts = payload.split("?iv=")
        require(parts.size == 2) { "Invalid NIP-04 payload format (expected base64?iv=base64)" }

        val ciphertext = Base64.decode(parts[0])
        val iv = Base64.decode(parts[1])

        return AesCbc.decrypt(ciphertext, sharedSecret, iv).decodeToString()
    }

    private fun randomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        val random = kotlin.random.Random
        for (i in bytes.indices) {
            bytes[i] = random.nextInt(256).toByte()
        }
        return bytes
    }
}
