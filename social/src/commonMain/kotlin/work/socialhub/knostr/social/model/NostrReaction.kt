package work.socialhub.knostr.social.model

import work.socialhub.knostr.entity.NostrEvent
import kotlin.js.JsExport

@JsExport
class NostrReaction {
    lateinit var event: NostrEvent
    var author: NostrUser? = null
    var targetEventId: String = ""
    /** Reaction content: "+" (like), "-" (dislike), or emoji */
    var content: String = "+"
    var createdAt: Long = 0
    /** NIP-30: custom emoji URL (null for standard reactions) */
    var emojiUrl: String? = null
}
