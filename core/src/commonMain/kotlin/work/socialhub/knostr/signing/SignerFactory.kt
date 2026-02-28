package work.socialhub.knostr.signing

/**
 * Create a NostrSigner from a private key hex string.
 * Uses the pure Kotlin secp256k1 cipher module (works on all platforms).
 */
fun createSigner(privateKeyHex: String): NostrSigner {
    return Secp256k1Signer(privateKeyHex)
}
