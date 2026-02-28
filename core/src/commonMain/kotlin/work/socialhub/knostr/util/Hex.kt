package work.socialhub.knostr.util

/**
 * Hex encoding/decoding utility.
 */
object Hex {

    private val hexChars = "0123456789abcdef".toCharArray()

    fun encode(bytes: ByteArray): String {
        val result = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            result[i * 2] = hexChars[v ushr 4]
            result[i * 2 + 1] = hexChars[v and 0x0F]
        }
        return result.concatToString()
    }

    fun decode(hex: String): ByteArray {
        val len = hex.length
        require(len % 2 == 0) { "Hex string must have even length" }
        val result = ByteArray(len / 2)
        for (i in result.indices) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "Invalid hex character at position ${i * 2}" }
            result[i] = ((hi shl 4) or lo).toByte()
        }
        return result
    }

    private object Character {
        fun digit(c: Char, radix: Int): Int {
            val d = when (c) {
                in '0'..'9' -> c - '0'
                in 'a'..'f' -> c - 'a' + 10
                in 'A'..'F' -> c - 'A' + 10
                else -> -1
            }
            return if (d < radix) d else -1
        }
    }
}
