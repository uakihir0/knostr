package work.socialhub.knostr.cipher

/**
 * Pure Kotlin ChaCha20 stream cipher implementation (RFC 8439).
 */
internal object ChaCha20 {

    /**
     * Encrypt/decrypt data using ChaCha20 (XOR with keystream).
     * @param data plaintext or ciphertext
     * @param key 32-byte key
     * @param nonce 12-byte nonce
     * @param counter initial block counter (default 0)
     * @return encrypted/decrypted data
     */
    fun encrypt(data: ByteArray, key: ByteArray, nonce: ByteArray, counter: Int = 0): ByteArray {
        require(key.size == 32) { "Key must be 32 bytes" }
        require(nonce.size == 12) { "Nonce must be 12 bytes" }

        val output = ByteArray(data.size)
        val numBlocks = (data.size + 63) / 64

        for (blockIdx in 0 until numBlocks) {
            val keystream = block(key, counter + blockIdx, nonce)
            val offset = blockIdx * 64
            val len = minOf(64, data.size - offset)
            for (i in 0 until len) {
                output[offset + i] = (data[offset + i].toInt() xor keystream[i].toInt()).toByte()
            }
        }
        return output
    }

    /**
     * Generate a 64-byte keystream block.
     */
    private fun block(key: ByteArray, counter: Int, nonce: ByteArray): ByteArray {
        val state = initState(key, counter, nonce)
        val working = state.copyOf()

        // 20 rounds (10 double rounds)
        repeat(10) {
            // Column rounds
            quarterRound(working, 0, 4, 8, 12)
            quarterRound(working, 1, 5, 9, 13)
            quarterRound(working, 2, 6, 10, 14)
            quarterRound(working, 3, 7, 11, 15)
            // Diagonal rounds
            quarterRound(working, 0, 5, 10, 15)
            quarterRound(working, 1, 6, 11, 12)
            quarterRound(working, 2, 7, 8, 13)
            quarterRound(working, 3, 4, 9, 14)
        }

        // Add original state
        for (i in 0 until 16) {
            working[i] += state[i]
        }

        // Serialize to little-endian bytes
        val result = ByteArray(64)
        for (i in 0 until 16) {
            result[i * 4] = working[i].toByte()
            result[i * 4 + 1] = (working[i] shr 8).toByte()
            result[i * 4 + 2] = (working[i] shr 16).toByte()
            result[i * 4 + 3] = (working[i] shr 24).toByte()
        }
        return result
    }

    /**
     * Initialize ChaCha20 state (16 x 32-bit words).
     * Layout: [constant, constant, constant, constant, key..., counter, nonce...]
     */
    private fun initState(key: ByteArray, counter: Int, nonce: ByteArray): IntArray {
        val state = IntArray(16)

        // Constants: "expand 32-byte k"
        state[0] = 0x61707865
        state[1] = 0x3320646e
        state[2] = 0x79622d32
        state[3] = 0x6b206574

        // Key (8 words, little-endian)
        for (i in 0 until 8) {
            state[4 + i] = littleEndianToInt(key, i * 4)
        }

        // Counter
        state[12] = counter

        // Nonce (3 words, little-endian)
        for (i in 0 until 3) {
            state[13 + i] = littleEndianToInt(nonce, i * 4)
        }

        return state
    }

    /**
     * ChaCha20 quarter round.
     */
    private fun quarterRound(state: IntArray, a: Int, b: Int, c: Int, d: Int) {
        state[a] += state[b]; state[d] = (state[d] xor state[a]).rotateLeft(16)
        state[c] += state[d]; state[b] = (state[b] xor state[c]).rotateLeft(12)
        state[a] += state[b]; state[d] = (state[d] xor state[a]).rotateLeft(8)
        state[c] += state[d]; state[b] = (state[b] xor state[c]).rotateLeft(7)
    }

    private fun Int.rotateLeft(n: Int): Int =
        (this shl n) or (this ushr (32 - n))

    private fun littleEndianToInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
