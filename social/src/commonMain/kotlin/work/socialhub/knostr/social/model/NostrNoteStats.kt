package work.socialhub.knostr.social.model

import kotlin.js.JsExport

@JsExport
class NostrNoteStats(
    var eventId: String,
    var likeCount: Int = 0,
    var replyCount: Int = 0,
    var repostCount: Int = 0,
)
