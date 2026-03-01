package work.socialhub.knostr.social.model

import work.socialhub.knostr.entity.NostrEvent
import kotlin.js.JsExport

@JsExport
class NostrNote {
    lateinit var event: NostrEvent
    var author: NostrUser? = null
    var content: String = ""
    var createdAt: Long = 0
    /** NIP-10: direct parent event ID (reply marker) */
    var replyToEventId: String? = null
    /** NIP-10: root event ID of the thread */
    var rootEventId: String? = null
    var reactions: List<NostrReaction> = listOf()
    var repostCount: Int = 0
    var replyCount: Int = 0
    /** NIP-19 note bech32 ID */
    var noteId: String = ""
    /** NIP-36 content warning reason (null if no warning) */
    var contentWarning: String? = null
    /** NIP-18: quoted event ID (q tag) */
    var quotedEventId: String? = null
}
