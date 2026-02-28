package work.socialhub.knostr.cipher

/**
 * BIP-340 tagged hash: SHA256(SHA256(tag) || SHA256(tag) || msg)
 */
internal object TaggedHash {

    // Pre-computed tag hashes for BIP-340
    private val auxTagHash = Sha256.digest("BIP0340/aux".encodeToByteArray())
    private val nonceTagHash = Sha256.digest("BIP0340/nonce".encodeToByteArray())
    private val challengeTagHash = Sha256.digest("BIP0340/challenge".encodeToByteArray())

    fun hash(tag: String, msg: ByteArray): ByteArray {
        val tagHash = when (tag) {
            "BIP0340/aux" -> auxTagHash
            "BIP0340/nonce" -> nonceTagHash
            "BIP0340/challenge" -> challengeTagHash
            else -> Sha256.digest(tag.encodeToByteArray())
        }
        return Sha256.digest(tagHash + tagHash + msg)
    }
}
