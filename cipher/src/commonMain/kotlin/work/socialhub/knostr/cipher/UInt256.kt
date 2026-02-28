package work.socialhub.knostr.cipher

/**
 * 256-bit unsigned integer represented as 8 x UInt words in big-endian order.
 * words[0] is the most significant word.
 *
 * All arithmetic operations return new instances (immutable).
 */
@OptIn(ExperimentalUnsignedTypes::class)
class UInt256 internal constructor(
    internal val words: UIntArray,
) : Comparable<UInt256> {

    init {
        require(words.size == 8) { "UInt256 requires exactly 8 words" }
    }

    companion object {
        val ZERO = UInt256(UIntArray(8))
        val ONE = UInt256(UIntArray(8).also { it[7] = 1u })
        val TWO = UInt256(UIntArray(8).also { it[7] = 2u })
        val THREE = UInt256(UIntArray(8).also { it[7] = 3u })

        fun fromInt(v: Int): UInt256 {
            require(v >= 0) { "fromInt requires non-negative value" }
            return UInt256(UIntArray(8).also { it[7] = v.toUInt() })
        }

        fun fromByteArray(bytes: ByteArray): UInt256 {
            require(bytes.size == 32) { "Expected 32 bytes, got ${bytes.size}" }
            val w = UIntArray(8)
            for (i in 0 until 8) {
                val offset = i * 4
                w[i] = ((bytes[offset].toInt() and 0xFF).toUInt() shl 24) or
                    ((bytes[offset + 1].toInt() and 0xFF).toUInt() shl 16) or
                    ((bytes[offset + 2].toInt() and 0xFF).toUInt() shl 8) or
                    (bytes[offset + 3].toInt() and 0xFF).toUInt()
            }
            return UInt256(w)
        }

        fun fromHex(hex: String): UInt256 {
            val padded = hex.padStart(64, '0')
            require(padded.length == 64) { "Hex string too long for UInt256" }
            val bytes = ByteArray(32)
            for (i in 0 until 32) {
                bytes[i] = ((hexDigit(padded[i * 2]) shl 4) or hexDigit(padded[i * 2 + 1])).toByte()
            }
            return fromByteArray(bytes)
        }

        private fun hexDigit(c: Char): Int = when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> throw IllegalArgumentException("Invalid hex char: $c")
        }
    }

    fun toByteArray(): ByteArray {
        val result = ByteArray(32)
        for (i in 0 until 8) {
            result[i * 4] = (words[i] shr 24).toByte()
            result[i * 4 + 1] = (words[i] shr 16).toByte()
            result[i * 4 + 2] = (words[i] shr 8).toByte()
            result[i * 4 + 3] = words[i].toByte()
        }
        return result
    }

    fun toHex(): String {
        val hexChars = "0123456789abcdef"
        val bytes = toByteArray()
        val sb = StringBuilder(64)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(hexChars[v ushr 4])
            sb.append(hexChars[v and 0x0F])
        }
        return sb.toString()
    }

    fun isZero(): Boolean = words.all { it == 0u }

    fun isEven(): Boolean = (words[7] and 1u) == 0u

    fun testBit(n: Int): Boolean {
        if (n < 0 || n >= 256) return false
        val wordIndex = 7 - (n / 32)
        val bitIndex = n % 32
        return (words[wordIndex] shr bitIndex) and 1u == 1u
    }

    fun bitLength(): Int {
        for (i in 0 until 8) {
            if (words[i] != 0u) {
                val topBits = 32 - words[i].countLeadingZeroBits()
                return (7 - i) * 32 + topBits
            }
        }
        return 0
    }

    // --- Arithmetic ---

    operator fun plus(other: UInt256): UInt256 {
        val result = UIntArray(8)
        var carry = 0uL
        for (i in 7 downTo 0) {
            val sum = words[i].toULong() + other.words[i].toULong() + carry
            result[i] = sum.toUInt()
            carry = sum shr 32
        }
        return UInt256(result)
    }

    operator fun minus(other: UInt256): UInt256 {
        val (result, _) = subtractWithBorrow(other)
        return result
    }

    /**
     * Subtract with borrow detection.
     * Returns (result, true) if borrow occurred (i.e., this < other).
     */
    fun subtractWithBorrow(other: UInt256): Pair<UInt256, Boolean> {
        val result = UIntArray(8)
        var borrow = 0uL
        for (i in 7 downTo 0) {
            val a = words[i].toULong()
            val b = other.words[i].toULong() + borrow
            if (a >= b) {
                result[i] = (a - b).toUInt()
                borrow = 0uL
            } else {
                result[i] = (a + 0x1_0000_0000uL - b).toUInt()
                borrow = 1uL
            }
        }
        return Pair(UInt256(result), borrow != 0uL)
    }

    /**
     * Full 512-bit multiplication. Returns (high256, low256).
     */
    fun mulFull(other: UInt256): Pair<UInt256, UInt256> {
        // 16-word result for full 512-bit product
        val result = ULongArray(16)

        for (i in 7 downTo 0) {
            var carry = 0uL
            for (j in 7 downTo 0) {
                val prod = words[i].toULong() * other.words[j].toULong() +
                    result[i + j + 1] + carry
                result[i + j + 1] = prod and 0xFFFF_FFFFuL
                carry = prod shr 32
            }
            result[i] += carry
        }

        val high = UIntArray(8) { result[it].toUInt() }
        val low = UIntArray(8) { result[it + 8].toUInt() }
        return Pair(UInt256(high), UInt256(low))
    }

    /**
     * Division and remainder: returns (quotient, remainder).
     * Uses bit-by-bit long division.
     */
    fun divRem(divisor: UInt256): Pair<UInt256, UInt256> {
        require(!divisor.isZero()) { "Division by zero" }

        if (this < divisor) return Pair(ZERO, this)
        if (this == divisor) return Pair(ONE, ZERO)

        val quotient = UIntArray(8)
        var remainder = ZERO

        val bits = bitLength()
        for (i in bits - 1 downTo 0) {
            // Left shift remainder by 1 and bring down next bit
            remainder = remainder.shl(1)
            if (testBit(i)) {
                remainder = remainder.or(ONE)
            }
            if (remainder >= divisor) {
                val (sub, _) = remainder.subtractWithBorrow(divisor)
                remainder = sub
                val wordIdx = 7 - (i / 32)
                val bitIdx = i % 32
                quotient[wordIdx] = quotient[wordIdx] or (1u shl bitIdx)
            }
        }

        return Pair(UInt256(quotient), remainder)
    }

    // --- Bit operations ---

    fun shl(n: Int): UInt256 {
        if (n == 0) return this
        if (n >= 256) return ZERO
        val wordShift = n / 32
        val bitShift = n % 32
        val result = UIntArray(8)
        if (bitShift == 0) {
            for (i in 0 until 8 - wordShift) {
                result[i] = words[i + wordShift]
            }
        } else {
            for (i in 0 until 8 - wordShift) {
                result[i] = words[i + wordShift] shl bitShift
                if (i + wordShift + 1 < 8) {
                    result[i] = result[i] or (words[i + wordShift + 1] shr (32 - bitShift))
                }
            }
        }
        return UInt256(result)
    }

    fun shr(n: Int): UInt256 {
        if (n == 0) return this
        if (n >= 256) return ZERO
        val wordShift = n / 32
        val bitShift = n % 32
        val result = UIntArray(8)
        if (bitShift == 0) {
            for (i in wordShift until 8) {
                result[i] = words[i - wordShift]
            }
        } else {
            for (i in wordShift until 8) {
                val src = i - wordShift
                result[i] = words[src] shr bitShift
                if (src > 0) {
                    result[i] = result[i] or (words[src - 1] shl (32 - bitShift))
                }
            }
        }
        return UInt256(result)
    }

    infix fun and(other: UInt256): UInt256 {
        val result = UIntArray(8)
        for (i in 0 until 8) result[i] = words[i] and other.words[i]
        return UInt256(result)
    }

    infix fun or(other: UInt256): UInt256 {
        val result = UIntArray(8)
        for (i in 0 until 8) result[i] = words[i] or other.words[i]
        return UInt256(result)
    }

    infix fun xor(other: UInt256): UInt256 {
        val result = UIntArray(8)
        for (i in 0 until 8) result[i] = words[i] xor other.words[i]
        return UInt256(result)
    }

    // --- Comparison ---

    override fun compareTo(other: UInt256): Int {
        for (i in 0 until 8) {
            if (words[i] != other.words[i]) {
                return words[i].compareTo(other.words[i])
            }
        }
        return 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UInt256) return false
        return words.contentEquals(other.words)
    }

    override fun hashCode(): Int = words.contentHashCode()

    override fun toString(): String = "UInt256(0x${toHex()})"
}
