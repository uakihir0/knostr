package work.socialhub.knostr.social.model

import work.socialhub.knostr.entity.NostrEvent
import kotlin.js.JsExport

@JsExport
class NostrList {
    lateinit var event: NostrEvent
    /** d-tag list name */
    var name: String = ""
    var pubkeys: List<String> = listOf()
    var createdAt: Long = 0
}
