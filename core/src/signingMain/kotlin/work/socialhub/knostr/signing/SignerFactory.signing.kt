package work.socialhub.knostr.signing

actual fun createSigner(privateKeyHex: String): NostrSigner {
    return Secp256k1Signer(privateKeyHex)
}
