package work.socialhub.knostr.cipher

/**
 * Pure Kotlin HMAC-SHA256 implementation (RFC 2104).
 * Uses the existing [Sha256] implementation.
 */
object HmacSha256 {

    private const val BLOCK_SIZE = 64

    fun compute(key: ByteArray, data: ByteArray): ByteArray {
        // If key is longer than block size, hash it first
        val paddedKey = when {
            key.size > BLOCK_SIZE -> Sha256.digest(key).copyOf(BLOCK_SIZE)
            key.size < BLOCK_SIZE -> key.copyOf(BLOCK_SIZE)
            else -> key.copyOf()
        }

        // iPad = key XOR 0x36
        val iPad = ByteArray(BLOCK_SIZE) { (paddedKey[it].toInt() xor 0x36).toByte() }
        // oPad = key XOR 0x5c
        val oPad = ByteArray(BLOCK_SIZE) { (paddedKey[it].toInt() xor 0x5c).toByte() }

        // HMAC = H(oPad || H(iPad || data))
        val inner = Sha256.digest(iPad + data)
        return Sha256.digest(oPad + inner)
    }
}
