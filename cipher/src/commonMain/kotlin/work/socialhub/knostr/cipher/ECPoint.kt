package work.socialhub.knostr.cipher

/**
 * A point on the secp256k1 elliptic curve.
 */
internal sealed class ECPoint {
    /** Affine coordinates (x, y). */
    data class Affine(val x: UInt256, val y: UInt256) : ECPoint()

    /** The point at infinity (identity element). */
    data object Infinity : ECPoint()
}

/**
 * Jacobian coordinate representation: (X, Y, Z) where
 * affine (x, y) = (X/Z^2, Y/Z^3).
 * Z == 0 represents the point at infinity.
 */
internal class JacobianPoint(val x: UInt256, val y: UInt256, val z: UInt256) {
    companion object {
        val INFINITY = JacobianPoint(UInt256.ONE, UInt256.ONE, UInt256.ZERO)

        fun fromAffine(pt: ECPoint): JacobianPoint = when (pt) {
            is ECPoint.Affine -> JacobianPoint(pt.x, pt.y, UInt256.ONE)
            is ECPoint.Infinity -> INFINITY
        }
    }

    fun isInfinity(): Boolean = z.isZero()
}
