package work.socialhub.knostr.social.model

import work.socialhub.knostr.entity.NostrEvent
import kotlin.js.JsExport

@JsExport
class NostrBadgeAward {
    lateinit var event: NostrEvent
    var badgeDTag: String = ""
    var recipientPubkeys: List<String> = listOf()
    var createdAt: Long = 0
}
