package work.socialhub.knostr.cipher

/**
 * Elliptic curve arithmetic on secp256k1: y^2 = x^3 + 7 (mod p).
 *
 * Internally uses Jacobian coordinates (X, Y, Z) where affine (x, y) = (X/Z^2, Y/Z^3).
 * This avoids modular inverses during point addition and doubling,
 * only requiring one inversion at the end when converting back to affine.
 *
 * Uses [Secp256k1Field] for fast modular arithmetic exploiting the special form of p.
 */
internal object ECMath {

    private val f = Secp256k1Field
    private val p = Secp256k1Curve.P
    private val seven = UInt256.fromHex("0000000000000000000000000000000000000000000000000000000000000007")

    /** Generator point G */
    val G = ECPoint.Affine(Secp256k1Curve.GX, Secp256k1Curve.GY)

    // --- Affine coordinate operations (public API) ---

    /**
     * Point addition: P + Q (affine).
     */
    fun add(p1: ECPoint, p2: ECPoint): ECPoint {
        val j1 = JacobianPoint.fromAffine(p1)
        val j2 = JacobianPoint.fromAffine(p2)
        return jacobianToAffine(jacobianAdd(j1, j2))
    }

    /**
     * Point doubling: 2P (affine).
     */
    fun double(pt: ECPoint): ECPoint {
        val j = JacobianPoint.fromAffine(pt)
        return jacobianToAffine(jacobianDouble(j))
    }

    /**
     * Scalar multiplication: k * P using double-and-add (MSB to LSB).
     * Uses Jacobian coordinates internally for performance.
     */
    fun multiply(k: UInt256, pt: ECPoint): ECPoint {
        if (k.isZero() || pt is ECPoint.Infinity) return ECPoint.Infinity

        val jp = JacobianPoint.fromAffine(pt)
        var result = JacobianPoint.INFINITY
        val bits = k.bitLength()

        for (i in bits - 1 downTo 0) {
            result = jacobianDouble(result)
            if (k.testBit(i)) {
                result = jacobianAdd(result, jp)
            }
        }
        return jacobianToAffine(result)
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
        val x3 = f.mul(f.mul(x, x), x)
        val c = f.add(x3, seven)

        // y = c^((p+1)/4) mod p
        val y = f.pow(c, Secp256k1Curve.P_PLUS_1_DIV_4)

        // Verify y^2 == c
        if (f.mul(y, y) != c) return null

        // Return point with even y
        return if (y.isEven()) {
            ECPoint.Affine(x, y)
        } else {
            ECPoint.Affine(x, f.neg(y))
        }
    }

    // --- Jacobian coordinate operations (internal) ---

    /**
     * Convert Jacobian point to affine.
     * Requires one modular inverse: z_inv = 1/Z, then x = X * z_inv^2, y = Y * z_inv^3.
     */
    private fun jacobianToAffine(jp: JacobianPoint): ECPoint {
        if (jp.isInfinity()) return ECPoint.Infinity
        if (jp.z == UInt256.ONE) return ECPoint.Affine(jp.x, jp.y)

        val zInv = f.inv(jp.z)
        val zInv2 = f.mul(zInv, zInv)
        val zInv3 = f.mul(zInv2, zInv)
        val ax = f.mul(jp.x, zInv2)
        val ay = f.mul(jp.y, zInv3)
        return ECPoint.Affine(ax, ay)
    }

    /**
     * Jacobian point addition.
     * Cost: 12M + 4S (no modular inverse needed).
     */
    private fun jacobianAdd(p1: JacobianPoint, p2: JacobianPoint): JacobianPoint {
        if (p1.isInfinity()) return p2
        if (p2.isInfinity()) return p1

        val z1sq = f.mul(p1.z, p1.z)
        val z2sq = f.mul(p2.z, p2.z)

        val u1 = f.mul(p1.x, z2sq)
        val u2 = f.mul(p2.x, z1sq)

        val s1 = f.mul(p1.y, f.mul(z2sq, p2.z))
        val s2 = f.mul(p2.y, f.mul(z1sq, p1.z))

        if (u1 == u2) {
            if (s1 == s2) {
                return jacobianDouble(p1)
            }
            return JacobianPoint.INFINITY
        }

        val h = f.sub(u2, u1)
        val r = f.sub(s2, s1)

        val h2 = f.mul(h, h)
        val h3 = f.mul(h2, h)
        val u1h2 = f.mul(u1, h2)

        val x3 = f.sub(f.sub(f.mul(r, r), h3), f.add(u1h2, u1h2))
        val y3 = f.sub(f.mul(r, f.sub(u1h2, x3)), f.mul(s1, h3))
        val z3 = f.mul(f.mul(h, p1.z), p2.z)

        return JacobianPoint(x3, y3, z3)
    }

    /**
     * Jacobian point doubling.
     * Cost: 4M + 6S (no modular inverse needed).
     * Uses the optimized formula for a=0 (secp256k1).
     */
    private fun jacobianDouble(jp: JacobianPoint): JacobianPoint {
        if (jp.isInfinity()) return JacobianPoint.INFINITY
        if (jp.y.isZero()) return JacobianPoint.INFINITY

        val ysq = f.mul(jp.y, jp.y)               // A = Y^2
        val fourB = f.mul(f.mul(UInt256.fromInt(4), jp.x), ysq) // 4*B = 4*X*Y^2
        val eightC = f.mul(UInt256.fromInt(8), f.mul(ysq, ysq)) // 8*C = 8*Y^4

        val xsq = f.mul(jp.x, jp.x)
        val d = f.add(f.add(xsq, xsq), xsq)       // D = 3*X^2

        val x3 = f.sub(f.mul(d, d), f.add(fourB, fourB)) // X3 = D^2 - 2*(4B)
        val y3 = f.sub(f.mul(d, f.sub(fourB, x3)), eightC) // Y3 = D*(4B - X3) - 8C
        val z3 = f.mul(f.add(jp.y, jp.y), jp.z)     // Z3 = 2*Y*Z

        return JacobianPoint(x3, y3, z3)
    }
}
