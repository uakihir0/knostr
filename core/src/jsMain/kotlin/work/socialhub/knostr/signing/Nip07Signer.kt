package work.socialhub.knostr.signing

import kotlinx.coroutines.await
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.UnsignedEvent
import kotlin.js.Promise

/**
 * NIP-07 browser extension signer (JS platform only).
 *
 * Uses `window.nostr` provided by browser extensions like nos2x, Alby, etc.
 * Since window.nostr methods return Promises and NostrSigner is synchronous,
 * this class provides both:
 * - Synchronous NostrSigner interface (throws UnsupportedOperationException)
 * - Suspend functions for proper async usage (signAsync, nip04/nip44 EncryptAsync/DecryptAsync)
 *
 * Create via [Nip07Signer.create] which awaits the public key from the extension.
 */
class Nip07Signer private constructor(
    private val publicKey: String,
) : NostrSigner {

    companion object {
        private val windowNostr: dynamic
            get() = js("(typeof window !== 'undefined' && window.nostr) ? window.nostr : null")

        /**
         * Check if NIP-07 extension is available (window.nostr exists).
         */
        fun isAvailable(): Boolean {
            return windowNostr != null
        }

        /**
         * Create a Nip07Signer by fetching the public key from the extension.
         */
        suspend fun create(): Nip07Signer {
            val nostr = windowNostr
                ?: throw NostrException("NIP-07 extension not available (window.nostr not found)")
            val pubkey = (nostr.getPublicKey() as Promise<String>).await()
            return Nip07Signer(pubkey)
        }
    }

    override fun getPublicKey(): String = publicKey

    /**
     * Synchronous sign — not supported on JS.
     * Use [signAsync] instead.
     */
    override fun sign(event: UnsignedEvent): NostrEvent {
        throw UnsupportedOperationException(
            "NIP-07 sign is async. Use signAsync() suspend function instead."
        )
    }

    /**
     * Sign an event asynchronously using the NIP-07 extension.
     */
    suspend fun signAsync(event: UnsignedEvent): NostrEvent {
        val nostr = windowNostr
            ?: throw NostrException("NIP-07 extension not available")

        val eventObj: dynamic = js("{}")
        eventObj.pubkey = publicKey
        eventObj.created_at = event.createdAt
        eventObj.kind = event.kind
        eventObj.content = event.content
        eventObj.tags = event.tags.map { it.toTypedArray() }.toTypedArray()

        val signed: dynamic = (nostr.signEvent(eventObj) as Promise<dynamic>).await()

        return NostrEvent(
            id = signed.id as String,
            pubkey = signed.pubkey as String,
            createdAt = (signed.created_at as Number).toLong(),
            kind = (signed.kind as Number).toInt(),
            tags = event.tags,
            content = signed.content as String,
            sig = signed.sig as String,
        )
    }

    override fun computeEventId(event: UnsignedEvent): String {
        throw UnsupportedOperationException(
            "NIP-07 does not expose computeEventId. Use signAsync() which computes the ID internally."
        )
    }

    override fun nip44Encrypt(plaintext: String, recipientPubkey: String): String {
        throw UnsupportedOperationException(
            "NIP-07 nip44Encrypt is async. Use nip44EncryptAsync() suspend function instead."
        )
    }

    override fun nip44Decrypt(payload: String, senderPubkey: String): String {
        throw UnsupportedOperationException(
            "NIP-07 nip44Decrypt is async. Use nip44DecryptAsync() suspend function instead."
        )
    }

    override fun nip04Encrypt(plaintext: String, recipientPubkey: String): String {
        throw UnsupportedOperationException(
            "NIP-07 nip04Encrypt is async. Use nip04EncryptAsync() suspend function instead."
        )
    }

    override fun nip04Decrypt(ciphertext: String, senderPubkey: String): String {
        throw UnsupportedOperationException(
            "NIP-07 nip04Decrypt is async. Use nip04DecryptAsync() suspend function instead."
        )
    }

    /** Encrypt plaintext using NIP-44 via the browser extension */
    suspend fun nip44EncryptAsync(plaintext: String, recipientPubkey: String): String {
        val nostr = windowNostr
            ?: throw NostrException("NIP-07 extension not available")
        return (nostr.nip44.encrypt(recipientPubkey, plaintext) as Promise<String>).await()
    }

    /** Decrypt NIP-44 payload via the browser extension */
    suspend fun nip44DecryptAsync(payload: String, senderPubkey: String): String {
        val nostr = windowNostr
            ?: throw NostrException("NIP-07 extension not available")
        return (nostr.nip44.decrypt(senderPubkey, payload) as Promise<String>).await()
    }

    /** Encrypt plaintext using NIP-04 via the browser extension */
    suspend fun nip04EncryptAsync(plaintext: String, recipientPubkey: String): String {
        val nostr = windowNostr
            ?: throw NostrException("NIP-07 extension not available")
        return (nostr.nip04.encrypt(recipientPubkey, plaintext) as Promise<String>).await()
    }

    /** Decrypt NIP-04 payload via the browser extension */
    suspend fun nip04DecryptAsync(ciphertext: String, senderPubkey: String): String {
        val nostr = windowNostr
            ?: throw NostrException("NIP-07 extension not available")
        return (nostr.nip04.decrypt(senderPubkey, ciphertext) as Promise<String>).await()
    }
}
