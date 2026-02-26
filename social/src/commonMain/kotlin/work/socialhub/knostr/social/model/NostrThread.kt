package work.socialhub.knostr.social.model

import kotlin.js.JsExport

@JsExport
class NostrThread {
    var rootNote: NostrNote? = null
    var replies: List<NostrNote> = listOf()
}
