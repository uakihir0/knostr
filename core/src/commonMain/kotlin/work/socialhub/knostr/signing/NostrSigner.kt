package work.socialhub.knostr.signing

import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.UnsignedEvent

/**
 * Interface for signing Nostr events.
 * Implementations must provide Schnorr signature (BIP-340) over secp256k1.
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
}
