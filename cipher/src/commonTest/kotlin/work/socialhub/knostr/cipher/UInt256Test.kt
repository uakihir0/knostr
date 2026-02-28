package work.socialhub.knostr.cipher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UInt256Test {

    @Test
    fun testZeroAndOne() {
        assertTrue(UInt256.ZERO.isZero())
        assertFalse(UInt256.ONE.isZero())
        assertTrue(UInt256.ZERO.isEven())
        assertFalse(UInt256.ONE.isEven())
    }

    @Test
    fun testFromHexRoundTrip() {
        val hex = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
        val n = UInt256.fromHex(hex)
        assertEquals(hex, n.toHex())
    }

    @Test
    fun testFromByteArrayRoundTrip() {
        val hex = "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f"
        val n = UInt256.fromHex(hex)
        val bytes = n.toByteArray()
        val recovered = UInt256.fromByteArray(bytes)
        assertEquals(hex, recovered.toHex())
    }

    @Test
    fun testAddition() {
        val a = UInt256.fromHex("0000000000000000000000000000000000000000000000000000000000000001")
        val b = UInt256.fromHex("0000000000000000000000000000000000000000000000000000000000000002")
        val c = a + b
        assertEquals(UInt256.THREE, c)
    }

    @Test
    fun testAdditionWithCarry() {
        val a = UInt256.fromHex("00000000000000000000000000000000000000000000000000000000ffffffff")
        val b = UInt256.ONE
        val c = a + b
        assertEquals(
            UInt256.fromHex("0000000000000000000000000000000000000000000000000000000100000000"),
            c
        )
    }

    @Test
    fun testAdditionCarryAcrossMultipleWords() {
        val a = UInt256.fromHex("000000000000000000000000000000000000000000000000ffffffffffffffff")
        val b = UInt256.ONE
        val c = a + b
        assertEquals(
            UInt256.fromHex("0000000000000000000000000000000000000000000000010000000000000000"),
            c
        )
    }

    @Test
    fun testSubtraction() {
        val a = UInt256.THREE
        val b = UInt256.ONE
        val c = a - b
        assertEquals(UInt256.TWO, c)
    }

    @Test
    fun testSubtractionWithBorrow() {
        val a = UInt256.fromHex("0000000000000000000000000000000000000000000000000000000100000000")
        val b = UInt256.ONE
        val c = a - b
        assertEquals(
            UInt256.fromHex("00000000000000000000000000000000000000000000000000000000ffffffff"),
            c
        )
    }

    @Test
    fun testSubtractWithBorrowDetection() {
        val (_, borrow1) = UInt256.THREE.subtractWithBorrow(UInt256.ONE)
        assertFalse(borrow1)

        val (_, borrow2) = UInt256.ONE.subtractWithBorrow(UInt256.THREE)
        assertTrue(borrow2)
    }

    @Test
    fun testMultiplication() {
        val a = UInt256.fromHex("0000000000000000000000000000000000000000000000000000000000000003")
        val b = UInt256.fromHex("0000000000000000000000000000000000000000000000000000000000000005")
        val (high, low) = a.mulFull(b)
        assertTrue(high.isZero())
        assertEquals(
            UInt256.fromHex("000000000000000000000000000000000000000000000000000000000000000f"),
            low
        )
    }

    @Test
    fun testMultiplicationLarger() {
        // 0xFFFFFFFF * 0xFFFFFFFF = 0xFFFFFFFE00000001
        val a = UInt256.fromHex("00000000000000000000000000000000000000000000000000000000ffffffff")
        val b = UInt256.fromHex("00000000000000000000000000000000000000000000000000000000ffffffff")
        val (high, low) = a.mulFull(b)
        assertTrue(high.isZero())
        assertEquals(
            UInt256.fromHex("000000000000000000000000000000000000000000000000fffffffe00000001"),
            low
        )
    }

    @Test
    fun testDivRem() {
        val a = UInt256.fromHex("000000000000000000000000000000000000000000000000000000000000000f") // 15
        val b = UInt256.fromHex("0000000000000000000000000000000000000000000000000000000000000004") // 4
        val (q, r) = a.divRem(b)
        // 15 / 4 = 3 remainder 3
        assertEquals(UInt256.THREE, q)
        assertEquals(UInt256.THREE, r)
    }

    @Test
    fun testDivRemIdentity() {
        // (a / b) * b + (a % b) == a
        val a = UInt256.fromHex("79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798")
        val b = UInt256.fromHex("00000000000000000000000000000000000000000000000d1234567890abcdef")
        val (q, r) = a.divRem(b)
        val (_, product) = q.mulFull(b)
        val reconstructed = product + r
        assertEquals(a, reconstructed)
    }

    @Test
    fun testComparison() {
        assertTrue(UInt256.ONE < UInt256.TWO)
        assertTrue(UInt256.THREE > UInt256.TWO)
        assertEquals(0, UInt256.ONE.compareTo(UInt256.ONE))

        val large = UInt256.fromHex("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
        assertTrue(large > UInt256.ONE)
    }

    @Test
    fun testBitLength() {
        assertEquals(0, UInt256.ZERO.bitLength())
        assertEquals(1, UInt256.ONE.bitLength())
        assertEquals(2, UInt256.TWO.bitLength())
        assertEquals(2, UInt256.THREE.bitLength())
        assertEquals(256, UInt256.fromHex("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff").bitLength())
    }

    @Test
    fun testTestBit() {
        // 5 = 0b101
        val five = UInt256.fromHex("0000000000000000000000000000000000000000000000000000000000000005")
        assertTrue(five.testBit(0))
        assertFalse(five.testBit(1))
        assertTrue(five.testBit(2))
        assertFalse(five.testBit(3))
    }

    @Test
    fun testShl() {
        val one = UInt256.ONE
        val two = one.shl(1)
        assertEquals(UInt256.TWO, two)

        // Shift across word boundary
        val shifted = UInt256.ONE.shl(32)
        assertEquals(
            UInt256.fromHex("0000000000000000000000000000000000000000000000000000000100000000"),
            shifted
        )
    }

    @Test
    fun testShr() {
        val two = UInt256.TWO
        val one = two.shr(1)
        assertEquals(UInt256.ONE, one)

        // Shift across word boundary
        val n = UInt256.fromHex("0000000000000000000000000000000000000000000000000000000100000000")
        val shifted = n.shr(32)
        assertEquals(UInt256.ONE, shifted)
    }

    @Test
    fun testXor() {
        val a = UInt256.fromHex("ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00")
        val b = UInt256.fromHex("0ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff0")
        val c = a xor b
        assertEquals(
            UInt256.fromHex("f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0"),
            c
        )
    }

    @Test
    fun testAndOr() {
        val a = UInt256.fromHex("f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0")
        val b = UInt256.fromHex("0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f")
        assertEquals(UInt256.ZERO, a and b)
        assertEquals(
            UInt256.fromHex("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
            a or b
        )
    }
}
