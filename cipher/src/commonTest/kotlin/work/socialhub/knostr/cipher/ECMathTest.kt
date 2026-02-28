package work.socialhub.knostr.cipher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ECMathTest {

    @Test
    fun testGeneratorPointOnCurve() {
        // Verify G.y^2 == G.x^3 + 7 mod p
        val field = Secp256k1Curve.field
        val g = ECMath.G
        val y2 = field.mul(g.y, g.y)
        val x3 = field.mul(field.mul(g.x, g.x), g.x)
        val rhs = field.add(x3, UInt256.fromHex("0000000000000000000000000000000000000000000000000000000000000007"))
        assertEquals(y2, rhs)
    }

    @Test
    fun testMultiplyByOne() {
        val result = ECMath.multiply(UInt256.ONE, ECMath.G)
        assertTrue(result is ECPoint.Affine)
        assertEquals(ECMath.G, result)
    }

    @Test
    fun testMultiplyByTwo() {
        val doubled = ECMath.double(ECMath.G)
        val multiplied = ECMath.multiply(UInt256.TWO, ECMath.G)
        assertEquals(doubled, multiplied)
    }

    @Test
    fun testMultiplyByThree() {
        val g2 = ECMath.double(ECMath.G)
        val g3_add = ECMath.add(g2, ECMath.G)
        val g3_mul = ECMath.multiply(UInt256.THREE, ECMath.G)
        assertEquals(g3_add, g3_mul)
    }

    @Test
    fun testAdditionAssociativity() {
        // (2G + 3G) == 5G
        val g2 = ECMath.multiply(UInt256.TWO, ECMath.G)
        val g3 = ECMath.multiply(UInt256.THREE, ECMath.G)
        val g5_add = ECMath.add(g2, g3)
        val five = UInt256.fromHex("0000000000000000000000000000000000000000000000000000000000000005")
        val g5_mul = ECMath.multiply(five, ECMath.G)
        assertEquals(g5_mul, g5_add)
    }

    @Test
    fun testKnownPublicKey() {
        // BIP-340 test vector 0: private key = 3
        val privKey = UInt256.THREE
        val result = ECMath.multiply(privKey, ECMath.G)
        assertTrue(result is ECPoint.Affine)
        // Known public key for private key 3
        assertEquals(
            "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9",
            result.x.toHex()
        )
    }

    @Test
    fun testLiftX() {
        val g = ECMath.G
        val lifted = ECMath.liftX(g.x)
        assertNotNull(lifted)
        assertEquals(g.x, lifted.x)
        // liftX returns even y
        assertTrue(lifted.y.isEven())
        // Should be the same y or p - y
        val field = Secp256k1Curve.field
        assertTrue(lifted.y == g.y || lifted.y == field.neg(g.y))
    }

    @Test
    fun testHasEvenY() {
        val g = ECMath.G
        // G.y is known to be even
        assertEquals(true, ECMath.hasEvenY(g))
    }

    @Test
    fun testAddInverse() {
        // P + (-P) = Infinity
        val g = ECMath.G
        val negG = ECPoint.Affine(g.x, Secp256k1Curve.field.neg(g.y))
        val result = ECMath.add(g, negG)
        assertTrue(result is ECPoint.Infinity)
    }

    @Test
    fun testMultiplyByKnownPrivateKey() {
        // Test vector: private key from knostr test
        val privKey = UInt256.fromHex("67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa")
        val result = ECMath.multiply(privKey, ECMath.G)
        assertTrue(result is ECPoint.Affine)
        // Just verify it produces a valid point (non-zero, on curve)
        val field = Secp256k1Curve.field
        val y2 = field.mul(result.y, result.y)
        val x3 = field.mul(field.mul(result.x, result.x), result.x)
        val rhs = field.add(x3, UInt256.fromHex("0000000000000000000000000000000000000000000000000000000000000007"))
        assertEquals(y2, rhs)
    }
}
