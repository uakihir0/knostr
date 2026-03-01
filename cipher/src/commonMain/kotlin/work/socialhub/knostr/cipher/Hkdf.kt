package work.socialhub.knostr.cipher

/**
 * Pure Kotlin HKDF implementation (RFC 5869) using HMAC-SHA256.
 */
internal object Hkdf {

    private const val HASH_LEN = 32 // SHA-256 output length

    /**
     * HKDF-Extract: PRK = HMAC-Hash(salt, IKM)
     */
    fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val actualSalt = if (salt.isEmpty()) ByteArray(HASH_LEN) else salt
        return HmacSha256.compute(actualSalt, ikm)
    }

    /**
     * HKDF-Expand: OKM = T(1) || T(2) || ... || T(N) truncated to length bytes.
     * T(i) = HMAC-Hash(PRK, T(i-1) || info || i)
     */
    fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length <= 255 * HASH_LEN) { "Output length exceeds maximum (255 * $HASH_LEN)" }

        val n = (length + HASH_LEN - 1) / HASH_LEN
        val okm = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0

        for (i in 1..n) {
            t = HmacSha256.compute(prk, t + info + byteArrayOf(i.toByte()))
            val copyLen = minOf(HASH_LEN, length - offset)
            t.copyInto(okm, offset, 0, copyLen)
            offset += copyLen
        }
        return okm
    }
}
