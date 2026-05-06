package work.socialhub.knostr.social.model

import kotlin.js.JsExport

@JsExport
class NostrRelationship {
    var isFollowing: Boolean = false
    var isFollowedBy: Boolean = false
    var isMuting: Boolean = false
}
