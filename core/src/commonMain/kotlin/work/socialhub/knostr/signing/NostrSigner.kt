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
}
