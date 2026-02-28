package work.socialhub.knostr.cipher

/**
 * Optimized field arithmetic for secp256k1 (mod p where p = 2^256 - 2^32 - 977).
 *
 * Exploits the special form of p for fast modular reduction:
 * Since 2^256 ≡ 2^32 + 977 (mod p), a 512-bit product (high, low) can be reduced
 * by computing: low + high * (2^32 + 977), which avoids expensive generic division.
 */
@OptIn(ExperimentalUnsignedTypes::class)
internal object Secp256k1Field {

    val P = Secp256k1Curve.P

    fun mod(a: UInt256): UInt256 {
        return if (a >= P) {
            var r = a
            while (r >= P) {
                r = r - P
            }
            r
        } else {
            a
        }
    }

    /**
     * Fast reduction of a 512-bit value (high, low) mod p.
     *
     * Uses the identity: 2^256 ≡ 2^32 + 977 (mod p).
     * Computes: low + high * (2^32 + 977) mod p.
     *
     * high * (2^32 + 977) = (high << 32) + high * 977.
     * We compute each as a 9-word number, add them plus low,
     * then reduce the overflow.
     */
    fun mod512(high: UInt256, low: UInt256): UInt256 {
        if (high.isZero()) return mod(low)

        val h = high.words
        val l = low.words

        // We'll accumulate everything in a 9-word array (ULong to handle carries).
        // Word 0 = most significant (overflow), words 1-8 = 256-bit result.

        // Start with low in positions 1-8
        val acc = ULongArray(9)
        for (i in 0 until 8) {
            acc[i + 1] = l[i].toULong()
        }

        // Add high << 32: shifts words left by 1 position
        // h[0] → acc[0], h[1] → acc[1], ..., h[7] → acc[7]
        for (i in 0 until 8) {
            acc[i] = acc[i] + h[i].toULong()
        }

        // Add high * 977: small multiply with carry
        var carry = 0uL
        for (i in 7 downTo 0) {
            val prod = h[i].toULong() * 977uL + carry
            acc[i + 1] = acc[i + 1] + (prod and 0xFFFFFFFFuL)
            carry = prod shr 32
        }
        acc[0] = acc[0] + carry

        // Now propagate carries through the accumulator (from LSB to MSB)
        for (i in 8 downTo 1) {
            acc[i - 1] = acc[i - 1] + (acc[i] shr 32)
            acc[i] = acc[i] and 0xFFFFFFFFuL
        }

        // acc[0] is the overflow, acc[1..8] is the 256-bit result
        var overflow = acc[0]
        val words = UIntArray(8) { acc[it + 1].toUInt() }
        var result = UInt256(words)

        // Reduce overflow: overflow * 2^256 ≡ overflow * (2^32 + 977) (mod p)
        // overflow is at most ~34 bits. overflow * 977 fits in ~44 bits.
        // overflow << 32 could be up to ~66 bits. Together fits in ~67 bits.
        while (overflow > 0uL) {
            // overflow * (2^32 + 977) split into two parts to avoid ULong overflow:
            //   overflow * 977 (fits in ~44 bits)
            //   overflow << 32 (fits in ~66 bits)
            val ov977 = overflow * 977uL
            val ovShift = overflow shl 32  // might lose top bits if overflow > 32 bits
            // Actually if overflow > 32 bits, shifting left 32 overflows ULong.
            // But overflow is at most ~34 bits at first pass, ~3 bits at second pass.
            // After first reduction, new overflow is tiny (~3 bits max), so this terminates.

            // Safer: add them word by word
            val rw = result.words.copyOf()
            var c = rw[7].toULong() + (ov977 and 0xFFFFFFFFuL)
            rw[7] = c.toUInt()
            c = (c shr 32) + rw[6].toULong() + (ov977 shr 32) + (overflow and 0xFFFFFFFFuL) // ov977 high + overflow low (= ovShift word 6)
            rw[6] = c.toUInt()
            c = (c shr 32) + rw[5].toULong() + (overflow shr 32) // ovShift word 5
            rw[5] = c.toUInt()
            c = c shr 32
            var j = 4
            while (c > 0uL && j >= 0) {
                c = c + rw[j].toULong()
                rw[j] = c.toUInt()
                c = c shr 32
                j--
            }
            overflow = c // new overflow (should be 0 or very small)
            result = UInt256(rw)
        }

        return finalReduce(result)
    }

    /** Subtract p at most a few times until result < p. */
    private fun finalReduce(v: UInt256): UInt256 {
        var r = v
        while (r >= P) {
            r = r - P
        }
        return r
    }

    fun add(a: UInt256, b: UInt256): UInt256 {
        val sum = a + b
        val overflow = sum < a
        if (!overflow) {
            return if (sum >= P) sum - P else sum
        }
        // Overflow: actual = sum + 2^256 ≡ sum + 2^32 + 977 (mod p)
        val correction = UInt256(UIntArray(8).also {
            it[6] = 1u       // 2^32
            it[7] = 977u     // + 977
        })
        val corrected = sum + correction
        return if (corrected < sum) {
            // Double overflow: add (2^32 + 977) again
            finalReduce(corrected + correction)
        } else {
            finalReduce(corrected)
        }
    }

    fun sub(a: UInt256, b: UInt256): UInt256 {
        val (result, borrowed) = a.subtractWithBorrow(b)
        return if (borrowed) {
            result + P
        } else {
            result
        }
    }

    fun mul(a: UInt256, b: UInt256): UInt256 {
        val (high, low) = a.mulFull(b)
        return mod512(high, low)
    }

    fun neg(a: UInt256): UInt256 {
        return if (a.isZero()) UInt256.ZERO else P - a
    }

    /**
     * Modular inverse using Fermat's little theorem: a^(p-2) mod p
     */
    fun inv(a: UInt256): UInt256 {
        require(!a.isZero()) { "Cannot invert zero" }
        val exp = P - UInt256.TWO
        return pow(a, exp)
    }

    /**
     * Modular exponentiation using square-and-multiply.
     */
    fun pow(base: UInt256, exp: UInt256): UInt256 {
        if (exp.isZero()) return UInt256.ONE
        var result = UInt256.ONE
        var b = mod(base)
        val bits = exp.bitLength()
        for (i in 0 until bits) {
            if (exp.testBit(i)) {
                result = mul(result, b)
            }
            if (i < bits - 1) {
                b = mul(b, b)
            }
        }
        return result
    }
}
