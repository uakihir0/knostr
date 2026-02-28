package work.socialhub.knostr.cipher

/**
 * A point on the secp256k1 elliptic curve (affine coordinates).
 */
internal sealed class ECPoint {
    data class Affine(val x: UInt256, val y: UInt256) : ECPoint()
    data object Infinity : ECPoint()
}
