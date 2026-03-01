package work.socialhub.knostr.cipher

/**
 * AES-256-CBC mode with PKCS7 padding.
 * Used by NIP-04 legacy encrypted DMs.
 */
object AesCbc {

    private const val BLOCK_SIZE = 16

    /**
     * Encrypt data using AES-256-CBC with PKCS7 padding.
     * @param plaintext data to encrypt
     * @param key 32-byte key
     * @param iv 16-byte initialization vector
     * @return encrypted data (multiple of 16 bytes)
     */
    fun encrypt(plaintext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        require(key.size == 32) { "Key must be 32 bytes" }
        require(iv.size == BLOCK_SIZE) { "IV must be $BLOCK_SIZE bytes" }

        val padded = pkcs7Pad(plaintext)
        val output = ByteArray(padded.size)
        var prevBlock = iv

        for (i in padded.indices step BLOCK_SIZE) {
            // XOR with previous ciphertext block (or IV for first block)
            val block = ByteArray(BLOCK_SIZE)
            for (j in 0 until BLOCK_SIZE) {
                block[j] = (padded[i + j].toInt() xor prevBlock[j].toInt()).toByte()
            }

            val encrypted = Aes.encryptBlock(block, key)
            encrypted.copyInto(output, i)
            prevBlock = encrypted
        }

        return output
    }

    /**
     * Decrypt data using AES-256-CBC with PKCS7 padding removal.
     * @param ciphertext encrypted data (must be multiple of 16 bytes)
     * @param key 32-byte key
     * @param iv 16-byte initialization vector
     * @return decrypted plaintext
     */
    fun decrypt(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        require(key.size == 32) { "Key must be 32 bytes" }
        require(iv.size == BLOCK_SIZE) { "IV must be $BLOCK_SIZE bytes" }
        require(ciphertext.size % BLOCK_SIZE == 0) { "Ciphertext must be multiple of $BLOCK_SIZE bytes" }
        require(ciphertext.isNotEmpty()) { "Ciphertext must not be empty" }

        val output = ByteArray(ciphertext.size)
        var prevBlock = iv

        for (i in ciphertext.indices step BLOCK_SIZE) {
            val block = ciphertext.copyOfRange(i, i + BLOCK_SIZE)
            val decrypted = Aes.decryptBlock(block, key)

            // XOR with previous ciphertext block (or IV)
            for (j in 0 until BLOCK_SIZE) {
                output[i + j] = (decrypted[j].toInt() xor prevBlock[j].toInt()).toByte()
            }
            prevBlock = block
        }

        return pkcs7Unpad(output)
    }

    private fun pkcs7Pad(data: ByteArray): ByteArray {
        val padLen = BLOCK_SIZE - (data.size % BLOCK_SIZE)
        val padded = ByteArray(data.size + padLen)
        data.copyInto(padded)
        for (i in data.size until padded.size) {
            padded[i] = padLen.toByte()
        }
        return padded
    }

    private fun pkcs7Unpad(data: ByteArray): ByteArray {
        require(data.isNotEmpty()) { "Data must not be empty" }
        val padLen = data.last().toInt() and 0xFF
        require(padLen in 1..BLOCK_SIZE) { "Invalid PKCS7 padding: $padLen" }
        // Verify all padding bytes are correct
        for (i in data.size - padLen until data.size) {
            require((data[i].toInt() and 0xFF) == padLen) { "Invalid PKCS7 padding" }
        }
        return data.copyOfRange(0, data.size - padLen)
    }
}
