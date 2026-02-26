package work.socialhub.knostr.signing

actual fun createSigner(privateKeyHex: String): NostrSigner {
    throw UnsupportedOperationException(
        "Secp256k1 signing is not available on this platform. " +
            "Use NostrConfig with a custom NostrSigner implementation instead."
    )
}
