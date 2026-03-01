package work.socialhub.knostr.social.model

import work.socialhub.knostr.entity.NostrEvent
import kotlin.js.JsExport

@JsExport
class NostrBadge {
    lateinit var event: NostrEvent
    var dTag: String = ""
    var name: String = ""
    var description: String = ""
    var image: String = ""
    var thumbImage: String = ""
    var createdAt: Long = 0
}
