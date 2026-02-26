package work.socialhub.knostr.signing

/**
 * Create a NostrSigner from a private key hex string.
 * Platform-specific: uses secp256k1-kmp on JVM/Native,
 * throws UnsupportedOperationException on JS.
 */
expect fun createSigner(privateKeyHex: String): NostrSigner
