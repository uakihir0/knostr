package work.socialhub.knostr.signing

import work.socialhub.knostr.cipher.Secp256k1
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.internal.InternalUtility
import work.socialhub.knostr.nip04.Nip04
import work.socialhub.knostr.nip44.Nip44
import work.socialhub.knostr.util.Hex

/**
 * Nostr event signer using secp256k1 Schnorr signatures (BIP-340).
 *
 * Event ID is computed as:
 *   SHA-256(JSON([0, pubkey, created_at, kind, tags, content]))
 *
 * Signature is a 64-byte Schnorr signature over the event ID.
 */
class Secp256k1Signer(
    private val privateKeyHex: String,
) : NostrSigner {

    private val privateKeyBytes: ByteArray = Hex.decode(privateKeyHex)
    private val publicKeyHex: String = Hex.encode(
        Secp256k1.pubkeyCreate(privateKeyBytes).drop(1).toByteArray()
    )

    override fun getPublicKey(): String = publicKeyHex

    override fun sign(event: UnsignedEvent): NostrEvent {
        val eventId = computeEventId(event)
        val eventIdBytes = Hex.decode(eventId)
        val sigBytes = Secp256k1.signSchnorr(eventIdBytes, privateKeyBytes, null)
        val sig = Hex.encode(sigBytes)

        return NostrEvent(
            id = eventId,
            pubkey = publicKeyHex,
            createdAt = event.createdAt,
            kind = event.kind,
            tags = event.tags,
            content = event.content,
            sig = sig,
        )
    }

    override fun computeEventId(event: UnsignedEvent): String {
        val serialized = InternalUtility.serializeForId(
            pubkey = event.pubkey.ifEmpty { publicKeyHex },
            createdAt = event.createdAt,
            kind = event.kind,
            tags = event.tags,
            content = event.content,
        )
        return InternalUtility.sha256Hex(serialized.encodeToByteArray())
    }

    override fun nip44Encrypt(plaintext: String, recipientPubkey: String): String {
        val convKey = Nip44.deriveConversationKey(privateKeyHex, recipientPubkey)
        return Nip44.encrypt(plaintext, convKey)
    }

    override fun nip44Decrypt(payload: String, senderPubkey: String): String {
        val convKey = Nip44.deriveConversationKey(privateKeyHex, senderPubkey)
        return Nip44.decrypt(payload, convKey)
    }

    override fun nip04Encrypt(plaintext: String, recipientPubkey: String): String {
        val shared = Nip04.computeSharedSecret(privateKeyHex, recipientPubkey)
        return Nip04.encrypt(plaintext, shared)
    }

    override fun nip04Decrypt(ciphertext: String, senderPubkey: String): String {
        val shared = Nip04.computeSharedSecret(privateKeyHex, senderPubkey)
        return Nip04.decrypt(ciphertext, shared)
    }
}
