package work.socialhub.knostr.social.model

import kotlin.js.JsExport

@JsExport
class SocialDataBatch(
    var users: List<NostrUser> = listOf(),
    var notes: List<NostrNote> = listOf(),
    var noteStats: List<NostrNoteStats> = listOf(),
) {
    fun isEmpty(): Boolean {
        return users.isEmpty() && notes.isEmpty() && noteStats.isEmpty()
    }
}
