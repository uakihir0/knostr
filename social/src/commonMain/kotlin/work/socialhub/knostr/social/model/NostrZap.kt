package work.socialhub.knostr.social.model

import work.socialhub.knostr.entity.NostrEvent
import kotlin.js.JsExport

@JsExport
class NostrZap {
    /** The zap receipt event (kind:9735) */
    lateinit var event: NostrEvent
    /** The sender of the zap */
    var sender: NostrUser? = null
    /** The recipient of the zap */
    var recipientPubkey: String = ""
    /** Target event ID (if zapping a specific note) */
    var targetEventId: String? = null
    /** Amount in millisatoshis */
    var amountMilliSats: Long = 0
    /** Zap message (from the zap request) */
    var message: String = ""
    var createdAt: Long = 0
}
