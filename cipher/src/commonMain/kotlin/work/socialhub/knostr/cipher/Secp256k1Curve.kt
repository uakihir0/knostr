package work.socialhub.knostr.cipher

/**
 * secp256k1 curve constants and arithmetic contexts.
 */
internal object Secp256k1Curve {

    /** Field prime: p = 2^256 - 2^32 - 977 */
    val P: UInt256 = UInt256.fromHex("fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f")

    /** Curve order */
    val N: UInt256 = UInt256.fromHex("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141")

    /** Curve parameter b = 7 */
    val B: UInt256 = UInt256.fromHex("0000000000000000000000000000000000000000000000000000000000000007")

    /** Generator point x-coordinate */
    val GX: UInt256 = UInt256.fromHex("79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798")

    /** Generator point y-coordinate */
    val GY: UInt256 = UInt256.fromHex("483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8")

    /** (p + 1) / 4 — used for modular square root since p mod 4 == 3 */
    val P_PLUS_1_DIV_4: UInt256 = UInt256.fromHex("3fffffffffffffffffffffffffffffffffffffffffffffffffffffffbfffff0c")

    /** Field arithmetic (mod p) */
    val field = ModularArithmetic(P)

    /** Scalar arithmetic (mod n) */
    val scalar = ModularArithmetic(N)
}
