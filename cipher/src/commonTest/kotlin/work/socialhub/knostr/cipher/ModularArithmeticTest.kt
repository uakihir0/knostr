package work.socialhub.knostr.cipher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModularArithmeticTest {

    private val field = Secp256k1Curve.field
    private val p = Secp256k1Curve.P

    @Test
    fun testModReducesCorrectly() {
        // p mod p == 0
        assertEquals(UInt256.ZERO, field.mod(p))
        // (p + 1) mod p == 1
        assertEquals(UInt256.ONE, field.mod(p + UInt256.ONE))
    }

    @Test
    fun testAddModP() {
        val a = p - UInt256.ONE  // p - 1
        val b = UInt256.TWO
        // (p - 1 + 2) mod p == 1
        assertEquals(UInt256.ONE, field.add(a, b))
    }

    @Test
    fun testSubModP() {
        val a = UInt256.ONE
        val b = UInt256.TWO
        // (1 - 2) mod p == p - 1
        assertEquals(p - UInt256.ONE, field.sub(a, b))
    }

    @Test
    fun testMulModP() {
        val a = UInt256.THREE
        val b = UInt256.fromHex("0000000000000000000000000000000000000000000000000000000000000005")
        // 3 * 5 = 15 mod p == 15
        val expected = UInt256.fromHex("000000000000000000000000000000000000000000000000000000000000000f")
        assertEquals(expected, field.mul(a, b))
    }

    @Test
    fun testNeg() {
        assertEquals(UInt256.ZERO, field.neg(UInt256.ZERO))
        assertEquals(p - UInt256.ONE, field.neg(UInt256.ONE))
    }

    @Test
    fun testInverse() {
        // a * inv(a) == 1 mod p
        val a = UInt256.fromHex("79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798")
        val aInv = field.inv(a)
        val product = field.mul(a, aInv)
        assertEquals(UInt256.ONE, product)
    }

    @Test
    fun testInverseOfTwo() {
        val inv2 = field.inv(UInt256.TWO)
        val result = field.mul(UInt256.TWO, inv2)
        assertEquals(UInt256.ONE, result)
    }

    @Test
    fun testPow() {
        // 2^10 = 1024
        val result = field.pow(UInt256.TWO, UInt256.fromHex("000000000000000000000000000000000000000000000000000000000000000a"))
        assertEquals(
            UInt256.fromHex("0000000000000000000000000000000000000000000000000000000000000400"),
            result
        )
    }

    @Test
    fun testScalarArithmetic() {
        val scalar = Secp256k1Curve.scalar
        val n = Secp256k1Curve.N

        // (n - 1 + 2) mod n == 1
        assertEquals(UInt256.ONE, scalar.add(n - UInt256.ONE, UInt256.TWO))

        // a * inv(a) == 1 mod n
        val a = UInt256.fromHex("67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa")
        val aInv = scalar.inv(a)
        assertEquals(UInt256.ONE, scalar.mul(a, aInv))
    }

    @Test
    fun testMod512() {
        // Test that mod512 with high=0 is the same as mod
        val a = UInt256.fromHex("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
        assertEquals(field.mod(a), field.mod512(UInt256.ZERO, a))
    }

    @Test
    fun testMod512NonZeroHigh() {
        // Verify: (1 * 2^256 + 0) mod p == 2^256 mod p
        // 2^256 mod p = 2^32 + 977 (since p = 2^256 - 2^32 - 977)
        val expected = UInt256.fromHex("00000000000000000000000000000000000000000000000000000001000003d1")
        val result = field.mod512(UInt256.ONE, UInt256.ZERO)
        assertEquals(expected, result)
    }

    @Test
    fun testSqrt() {
        // Verify sqrt: compute y^2, then sqrt, should get y back (if y is even)
        val y = Secp256k1Curve.GY
        val y2 = field.mul(y, y)
        val sqrtY2 = field.pow(y2, Secp256k1Curve.P_PLUS_1_DIV_4)
        // Should be y or p - y
        assertTrue(sqrtY2 == y || sqrtY2 == field.neg(y))
    }
}
