package work.socialhub.knostr.social.model

import kotlin.js.JsExport

@JsExport
class SocialDataRequest(
    var userPubkeys: List<String> = listOf(),
    var noteIds: List<String> = listOf(),
    var noteStatsEventIds: List<String> = listOf(),
) {
    fun isEmpty(): Boolean {
        return userPubkeys.isEmpty() && noteIds.isEmpty() && noteStatsEventIds.isEmpty()
    }
}
