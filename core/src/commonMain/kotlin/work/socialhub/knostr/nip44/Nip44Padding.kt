package work.socialhub.knostr.nip44

/**
 * NIP-44 v2 padding: power-of-2 with minimum 32 bytes.
 * Format: 2-byte big-endian length prefix + plaintext + zero-padding.
 */
internal object Nip44Padding {

    private const val MIN_SIZE = 32
    private const val MAX_PLAINTEXT_SIZE = 65535 // 2^16 - 1 (fits in 2-byte prefix)

    /**
     * Pad plaintext bytes according to NIP-44 spec.
     * @return padded byte array with 2-byte length prefix
     */
    fun pad(plaintext: ByteArray): ByteArray {
        require(plaintext.isNotEmpty()) { "Plaintext must not be empty" }
        require(plaintext.size <= MAX_PLAINTEXT_SIZE) { "Plaintext too large (max $MAX_PLAINTEXT_SIZE bytes)" }

        val paddedSize = calcPaddedSize(plaintext.size)
        val result = ByteArray(2 + paddedSize)

        // 2-byte big-endian length prefix
        result[0] = (plaintext.size shr 8).toByte()
        result[1] = (plaintext.size and 0xFF).toByte()

        // Copy plaintext
        plaintext.copyInto(result, 2)
        // Remaining bytes are already zero

        return result
    }

    /**
     * Remove padding, returning original plaintext bytes.
     */
    fun unpad(padded: ByteArray): ByteArray {
        require(padded.size >= 2 + MIN_SIZE) { "Padded data too short" }

        val len = ((padded[0].toInt() and 0xFF) shl 8) or (padded[1].toInt() and 0xFF)
        require(len > 0) { "Invalid length prefix: 0" }
        require(len <= padded.size - 2) { "Length prefix exceeds data" }

        // Verify expected padded size matches
        val expectedPaddedSize = calcPaddedSize(len)
        require(padded.size == 2 + expectedPaddedSize) { "Invalid padding size" }

        return padded.copyOfRange(2, 2 + len)
    }

    /**
     * Calculate padded size using NIP-44 power-of-2 algorithm.
     */
    private fun calcPaddedSize(unpaddedLen: Int): Int {
        if (unpaddedLen <= MIN_SIZE) return MIN_SIZE

        // Next power of 2 for the chunk count
        val nextPower = 1 shl (32 - (unpaddedLen - 1).countLeadingZeroBits())
        val chunk = if (nextPower <= 256) MIN_SIZE else nextPower / 8
        return if (unpaddedLen % chunk == 0) {
            unpaddedLen
        } else {
            (unpaddedLen / chunk + 1) * chunk
        }
    }

    private fun Int.countLeadingZeroBits(): Int {
        if (this == 0) return 32
        var n = 0
        var v = this
        if (v and 0xFFFF0000.toInt() == 0) { n += 16; v = v shl 16 }
        if (v and 0xFF000000.toInt() == 0) { n += 8; v = v shl 8 }
        if (v and 0xF0000000.toInt() == 0) { n += 4; v = v shl 4 }
        if (v and 0xC0000000.toInt() == 0) { n += 2; v = v shl 2 }
        if (v and 0x80000000.toInt() == 0) { n += 1 }
        return n
    }
}
