package work.socialhub.knostr.cipher

/**
 * Elliptic curve arithmetic on secp256k1: y^2 = x^3 + 7 (mod p).
 */
internal object ECMath {

    private val field = Secp256k1Curve.field
    private val p = Secp256k1Curve.P
    private val seven = UInt256.fromHex("0000000000000000000000000000000000000000000000000000000000000007")

    /** Generator point G */
    val G = ECPoint.Affine(Secp256k1Curve.GX, Secp256k1Curve.GY)

    /**
     * Point addition: P + Q
     */
    fun add(p1: ECPoint, p2: ECPoint): ECPoint {
        if (p1 is ECPoint.Infinity) return p2
        if (p2 is ECPoint.Infinity) return p1

        val a = p1 as ECPoint.Affine
        val b = p2 as ECPoint.Affine

        if (a.x == b.x) {
            if (a.y == b.y) {
                // P == Q -> double
                return double(a)
            } else {
                // P.x == Q.x but P.y != Q.y -> point at infinity (P = -Q)
                return ECPoint.Infinity
            }
        }

        // λ = (y2 - y1) / (x2 - x1) mod p
        val dy = field.sub(b.y, a.y)
        val dx = field.sub(b.x, a.x)
        val lambda = field.mul(dy, field.inv(dx))

        // xr = λ^2 - x1 - x2 mod p
        val lambda2 = field.mul(lambda, lambda)
        val xr = field.sub(field.sub(lambda2, a.x), b.x)

        // yr = λ(x1 - xr) - y1 mod p
        val yr = field.sub(field.mul(lambda, field.sub(a.x, xr)), a.y)

        return ECPoint.Affine(xr, yr)
    }

    /**
     * Point doubling: 2P
     */
    fun double(pt: ECPoint): ECPoint {
        if (pt is ECPoint.Infinity) return ECPoint.Infinity
        val a = pt as ECPoint.Affine

        if (a.y.isZero()) return ECPoint.Infinity

        // λ = 3x^2 / (2y) mod p  (curve a = 0 for secp256k1)
        val x2 = field.mul(a.x, a.x)
        val num = field.mul(UInt256.THREE, x2)
        val den = field.mul(UInt256.TWO, a.y)
        val lambda = field.mul(num, field.inv(den))

        // xr = λ^2 - 2x mod p
        val lambda2 = field.mul(lambda, lambda)
        val xr = field.sub(field.sub(lambda2, a.x), a.x)

        // yr = λ(x - xr) - y mod p
        val yr = field.sub(field.mul(lambda, field.sub(a.x, xr)), a.y)

        return ECPoint.Affine(xr, yr)
    }

    /**
     * Scalar multiplication: k * P using double-and-add (MSB to LSB).
     */
    fun multiply(k: UInt256, pt: ECPoint): ECPoint {
        if (k.isZero() || pt is ECPoint.Infinity) return ECPoint.Infinity

        var result: ECPoint = ECPoint.Infinity
        val bits = k.bitLength()

        for (i in bits - 1 downTo 0) {
            result = double(result)
            if (k.testBit(i)) {
                result = add(result, pt)
            }
        }
        return result
    }

    /**
     * Check if a point has even y-coordinate.
     */
    fun hasEvenY(pt: ECPoint.Affine): Boolean = pt.y.isEven()

    /**
     * Lift x-coordinate to a curve point with even y.
     * Returns null if x is not a valid x-coordinate on the curve.
     */
    fun liftX(x: UInt256): ECPoint.Affine? {
        if (x >= p) return null

        // c = x^3 + 7 mod p
        val x3 = field.mul(field.mul(x, x), x)
        val c = field.add(x3, seven)

        // y = c^((p+1)/4) mod p
        val y = field.pow(c, Secp256k1Curve.P_PLUS_1_DIV_4)

        // Verify y^2 == c
        if (field.mul(y, y) != c) return null

        // Return point with even y
        return if (y.isEven()) {
            ECPoint.Affine(x, y)
        } else {
            ECPoint.Affine(x, field.neg(y))
        }
    }
}
