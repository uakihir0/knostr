package work.socialhub.knostr.signing

import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.UnsignedEvent

/**
 * Interface for signing Nostr events.
 * Implementations must provide Schnorr signature (BIP-340) over secp256k1.
 *
 * The synchronous methods cover signers whose key material is available
 * locally ([Secp256k1Signer]). Signers whose key material lives outside the
 * process — a NIP-07 browser extension, a NIP-46 bunker, a hardware wallet —
 * cannot answer synchronously; they override the `*Async` variants instead and
 * leave the synchronous ones throwing. The SDK calls the `*Async` variants
 * everywhere, so a signer that only implements those works for every event the
 * SDK signs, including NIP-42 relay authentication.
 */
interface NostrSigner {
    /** Get the public key (hex encoded, 64 chars) */
    fun getPublicKey(): String

    /** Sign an unsigned event, producing a complete NostrEvent with id and sig */
    fun sign(event: UnsignedEvent): NostrEvent

    /** Compute the event ID (SHA-256 hash of serialized event) */
    fun computeEventId(event: UnsignedEvent): String

    /** Encrypt plaintext using NIP-44 v2 for the given recipient */
    fun nip44Encrypt(plaintext: String, recipientPubkey: String): String

    /** Decrypt a NIP-44 v2 payload from the given sender */
    fun nip44Decrypt(payload: String, senderPubkey: String): String

    /** Encrypt plaintext using NIP-04 (legacy) for the given recipient */
    fun nip04Encrypt(plaintext: String, recipientPubkey: String): String

    /** Decrypt a NIP-04 (legacy) payload from the given sender */
    fun nip04Decrypt(ciphertext: String, senderPubkey: String): String

    /** Async variant of [getPublicKey]; defaults to the synchronous call. */
    suspend fun getPublicKeyAsync(): String = getPublicKey()

    /** Async variant of [sign]; defaults to the synchronous call. */
    suspend fun signAsync(event: UnsignedEvent): NostrEvent = sign(event)

    /** Async variant of [nip44Encrypt]; defaults to the synchronous call. */
    suspend fun nip44EncryptAsync(plaintext: String, recipientPubkey: String): String =
        nip44Encrypt(plaintext, recipientPubkey)

    /** Async variant of [nip44Decrypt]; defaults to the synchronous call. */
    suspend fun nip44DecryptAsync(payload: String, senderPubkey: String): String =
        nip44Decrypt(payload, senderPubkey)

    /** Async variant of [nip04Encrypt]; defaults to the synchronous call. */
    suspend fun nip04EncryptAsync(plaintext: String, recipientPubkey: String): String =
        nip04Encrypt(plaintext, recipientPubkey)

    /** Async variant of [nip04Decrypt]; defaults to the synchronous call. */
    suspend fun nip04DecryptAsync(ciphertext: String, senderPubkey: String): String =
        nip04Decrypt(ciphertext, senderPubkey)
}
