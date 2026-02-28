package work.socialhub.knostr.cipher

/**
 * Pure Kotlin secp256k1 cryptographic operations.
 * Provides public key derivation, BIP-340 Schnorr signing and verification.
 */
object Secp256k1 {

    private val field = Secp256k1Curve.field
    private val scalar = Secp256k1Curve.scalar
    private val n = Secp256k1Curve.N
    private val p = Secp256k1Curve.P

    /**
     * Derive a compressed public key from a 32-byte private key.
     * Returns 33 bytes: 1 prefix byte (02 or 03) + 32-byte x-coordinate.
     */
    fun pubkeyCreate(privateKey: ByteArray): ByteArray {
        require(privateKey.size == 32) { "Private key must be 32 bytes" }
        val d = UInt256.fromByteArray(privateKey)
        require(!d.isZero() && d < n) { "Private key out of range" }

        val point = ECMath.multiply(d, ECMath.G)
        require(point is ECPoint.Affine) { "Invalid key: produces point at infinity" }

        val prefix = if (point.y.isEven()) 0x02.toByte() else 0x03.toByte()
        return byteArrayOf(prefix) + point.x.toByteArray()
    }

    /**
     * BIP-340 Schnorr signature.
     * @param message 32-byte message (event ID hash)
     * @param privateKey 32-byte private key
     * @param auxRand optional 32-byte auxiliary randomness (null = 32 zero bytes)
     * @return 64-byte signature (R.x || s)
     */
    fun signSchnorr(message: ByteArray, privateKey: ByteArray, auxRand: ByteArray?): ByteArray {
        require(message.size == 32) { "Message must be 32 bytes" }
        require(privateKey.size == 32) { "Private key must be 32 bytes" }

        // Step 1: d' = int(privateKey)
        val dPrime = UInt256.fromByteArray(privateKey)
        require(!dPrime.isZero() && dPrime < n) { "Private key out of range" }

        // Step 2: P = d' * G
        val pPoint = ECMath.multiply(dPrime, ECMath.G)
        require(pPoint is ECPoint.Affine) { "Invalid key" }

        // Step 3: d = d' if has_even_y(P), else n - d'
        val d = if (ECMath.hasEvenY(pPoint)) dPrime else scalar.neg(dPrime)

        // Step 4: auxiliary randomness
        val aux = auxRand ?: ByteArray(32)
        require(aux.size == 32) { "auxRand must be 32 bytes" }

        // Step 5: t = bytes(d) XOR taggedHash("BIP0340/aux", aux)
        val dBytes = d.toByteArray()
        val auxHash = TaggedHash.hash("BIP0340/aux", aux)
        val t = ByteArray(32) { (dBytes[it].toInt() xor auxHash[it].toInt()).toByte() }

        // Step 6: rand = taggedHash("BIP0340/nonce", t || bytes(P.x) || message)
        val rand = TaggedHash.hash("BIP0340/nonce", t + pPoint.x.toByteArray() + message)

        // Step 7: k' = int(rand) mod n
        val kPrime = scalar.mod(UInt256.fromByteArray(rand))
        require(!kPrime.isZero()) { "Nonce is zero" }

        // Step 8: R = k' * G
        val rPoint = ECMath.multiply(kPrime, ECMath.G)
        require(rPoint is ECPoint.Affine) { "Invalid nonce" }

        // Step 9: k = k' if has_even_y(R), else n - k'
        val k = if (ECMath.hasEvenY(rPoint)) kPrime else scalar.neg(kPrime)

        // Step 10: e = int(taggedHash("BIP0340/challenge", bytes(R.x) || bytes(P.x) || message)) mod n
        val eHash = TaggedHash.hash(
            "BIP0340/challenge",
            rPoint.x.toByteArray() + pPoint.x.toByteArray() + message
        )
        val e = scalar.mod(UInt256.fromByteArray(eHash))

        // Step 11: sig = bytes(R.x) || bytes((k + e*d) mod n)
        val s = scalar.add(k, scalar.mul(e, d))

        return rPoint.x.toByteArray() + s.toByteArray()
    }

    /**
     * BIP-340 Schnorr signature verification.
     * @param signature 64-byte signature
     * @param message 32-byte message
     * @param publicKey 32-byte x-only public key
     * @return true if signature is valid
     */
    fun verifySchnorr(signature: ByteArray, message: ByteArray, publicKey: ByteArray): Boolean {
        if (signature.size != 64 || message.size != 32 || publicKey.size != 32) return false

        // Step 1: P = lift_x(int(publicKey))
        val px = UInt256.fromByteArray(publicKey)
        val pPoint = ECMath.liftX(px) ?: return false

        // Step 2: r = int(sig[0:32])
        val r = UInt256.fromByteArray(signature.copyOfRange(0, 32))
        if (r >= p) return false

        // Step 3: s = int(sig[32:64])
        val s = UInt256.fromByteArray(signature.copyOfRange(32, 64))
        if (s >= n) return false

        // Step 4: e = int(taggedHash("BIP0340/challenge", sig[0:32] || bytes(P.x) || message)) mod n
        val eHash = TaggedHash.hash(
            "BIP0340/challenge",
            signature.copyOfRange(0, 32) + publicKey + message
        )
        val e = scalar.mod(UInt256.fromByteArray(eHash))

        // Step 5: R = s*G - e*P
        val sG = ECMath.multiply(s, ECMath.G)
        val eP = ECMath.multiply(e, pPoint)
        val negEP = when (eP) {
            is ECPoint.Affine -> ECPoint.Affine(eP.x, field.neg(eP.y))
            is ECPoint.Infinity -> ECPoint.Infinity
        }
        val rPoint = ECMath.add(sG, negEP)

        // Step 6: fail if R is infinity, or !has_even_y(R), or R.x != r
        if (rPoint is ECPoint.Infinity) return false
        val rAffine = rPoint as ECPoint.Affine
        if (!ECMath.hasEvenY(rAffine)) return false
        if (rAffine.x != r) return false

        return true
    }
}
