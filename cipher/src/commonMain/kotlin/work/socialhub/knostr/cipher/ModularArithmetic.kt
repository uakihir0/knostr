package work.socialhub.knostr.cipher

/**
 * Modular arithmetic over a given modulus.
 * Used for both field operations (mod p) and scalar operations (mod n).
 */
@OptIn(ExperimentalUnsignedTypes::class)
internal class ModularArithmetic(val modulus: UInt256) {

    fun mod(a: UInt256): UInt256 {
        return if (a >= modulus) {
            a.divRem(modulus).second
        } else {
            a
        }
    }

    /**
     * Reduce a 512-bit value (high, low) modulo the modulus.
     */
    fun mod512(high: UInt256, low: UInt256): UInt256 {
        if (high.isZero()) return mod(low)
        // Combine high and low into full division
        // high * 2^256 + low mod m
        // = ((high mod m) * (2^256 mod m) + low) mod m
        // We compute iteratively: shift high bits into a running remainder
        var remainder = UInt256.ZERO
        // Process high 256 bits
        val highBits = high.bitLength()
        for (i in highBits - 1 downTo 0) {
            remainder = add(remainder, remainder) // remainder * 2
            if (high.testBit(i)) {
                remainder = add(remainder, UInt256.ONE)
            }
        }
        // Now remainder = high mod m
        // We need remainder * 2^256 mod m + low mod m
        // Shift remainder left by 256 bits modularly (multiply by 2, 256 times)
        for (i in 0 until 256) {
            remainder = add(remainder, remainder)
        }
        // Add low
        remainder = add(remainder, mod(low))
        return remainder
    }

    fun add(a: UInt256, b: UInt256): UInt256 {
        val sum = a + b
        // Check for overflow: if sum < a, we wrapped around 2^256
        val (result, borrowed) = sum.subtractWithBorrow(modulus)
        return if (sum < a || !borrowed) {
            // overflow or sum >= modulus
            result
        } else {
            sum
        }
    }

    fun sub(a: UInt256, b: UInt256): UInt256 {
        val (result, borrowed) = a.subtractWithBorrow(b)
        return if (borrowed) {
            result + modulus
        } else {
            result
        }
    }

    fun mul(a: UInt256, b: UInt256): UInt256 {
        val (high, low) = a.mulFull(b)
        return mod512(high, low)
    }

    fun neg(a: UInt256): UInt256 {
        return if (a.isZero()) UInt256.ZERO else modulus - a
    }

    /**
     * Modular inverse using Fermat's little theorem: a^(m-2) mod m
     */
    fun inv(a: UInt256): UInt256 {
        require(!a.isZero()) { "Cannot invert zero" }
        val exp = modulus - UInt256.TWO
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
