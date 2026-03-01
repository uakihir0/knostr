package work.socialhub.knostr.social.model

import work.socialhub.knostr.entity.NostrEvent
import kotlin.js.JsExport

@JsExport
data class NostrDirectMessage(
    /** Event ID (from the Gift Wrap or kind:4 event) */
    val id: String,
    /** Sender's pubkey (hex) */
    val senderPubkey: String,
    /** Recipient's pubkey (hex) */
    val recipientPubkey: String,
    /** Decrypted message content */
    val content: String,
    /** Timestamp (epoch seconds) */
    val createdAt: Long,
    /** Original event (Gift Wrap for NIP-17, kind:4 for NIP-04) */
    val event: NostrEvent? = null,
    /** true = NIP-04 legacy, false = NIP-17 modern */
    val isLegacy: Boolean = false,
)
