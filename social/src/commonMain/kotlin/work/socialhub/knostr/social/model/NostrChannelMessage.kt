package work.socialhub.knostr.social.model

import work.socialhub.knostr.entity.NostrEvent
import kotlin.js.JsExport

@JsExport
class NostrChannelMessage {
    lateinit var event: NostrEvent
    var content: String = ""
    var channelId: String = ""
    var createdAt: Long = 0
}
