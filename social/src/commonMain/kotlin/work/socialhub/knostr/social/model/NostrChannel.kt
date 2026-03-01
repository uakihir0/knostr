package work.socialhub.knostr.social.model

import kotlin.js.JsExport

@JsExport
class NostrChannel {
    var id: String = ""
    var name: String = ""
    var about: String = ""
    var picture: String = ""
    var createdAt: Long = 0
}
