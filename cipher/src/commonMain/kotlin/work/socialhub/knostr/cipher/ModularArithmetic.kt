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
     * Uses bit-by-bit long division over the full 512-bit number.
     * remainder stays < modulus at all times (no overflow possible).
     */
    fun mod512(high: UInt256, low: UInt256): UInt256 {
        if (high.isZero()) return mod(low)

        var remainder = UInt256.ZERO
        // Process all 512 bits from MSB (bit 511) to LSB (bit 0)
        // Bit i of the 512-bit number:
        //   bits 511..256 → high.testBit(i - 256)
        //   bits 255..0   → low.testBit(i)
        for (i in 511 downTo 0) {
            // Left shift remainder by 1 (remainder < modulus < 2^256, so shl(1) fits in 257 bits)
            val shifted = remainder.shl(1)
            val carry = remainder.testBit(255) // the bit that was shifted out

            // Bring in the next bit from the dividend
            val bit = if (i >= 256) high.testBit(i - 256) else low.testBit(i)
            val withBit = if (bit) shifted or UInt256.ONE else shifted

            // Now the actual value is: withBit + carry * 2^256
            // We need to reduce mod modulus
            if (carry) {
                // Value is withBit + 2^256, which is >= modulus (since modulus < 2^256)
                // Subtract modulus: withBit + 2^256 - modulus
                // Since withBit < 2^256 and modulus < 2^256:
                //   withBit + 2^256 - modulus is in [1, 2^256)
                // Use subtractWithBorrow: if withBit >= modulus, result = withBit - modulus (< 2^256)
                //                         if withBit < modulus, result = withBit + 2^256 - modulus
                val (sub, _) = withBit.subtractWithBorrow(modulus)
                remainder = sub
            } else {
                // Value is just withBit (no carry), reduce if >= modulus
                remainder = if (withBit >= modulus) withBit - modulus else withBit
            }
        }
        return remainder
    }

    fun add(a: UInt256, b: UInt256): UInt256 {
        val sum = a + b
        val overflow = sum < a // wrapped around 2^256

        if (!overflow) {
            // No overflow: just reduce if >= modulus
            return if (sum >= modulus) sum - modulus else sum
        }

        // Overflow: actual value = sum + 2^256
        // Since a, b < modulus and modulus < 2^256:
        //   actual = a + b < 2 * modulus
        // So actual - modulus < modulus (fits in 256 bits)
        // If sum < modulus: result = sum + 2^256 - modulus (borrow gives us this)
        // If sum >= modulus: result = sum - modulus (no borrow), but need + 2^256
        //   actual - modulus = sum + 2^256 - modulus >= 2^256, need second subtraction
        //   actual - 2*modulus = sum - 2*modulus + 2^256 = (sum - modulus) - modulus + 2^256
        val (r1, _) = sum.subtractWithBorrow(modulus)
        if (sum >= modulus) {
            // sum >= modulus AND overflow: actual = sum + 2^256, actual - modulus >= 2^256
            // Need actual - 2*modulus = r1 + 2^256 - modulus
            val (r2, _) = r1.subtractWithBorrow(modulus)
            return r2
        }
        return r1
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
