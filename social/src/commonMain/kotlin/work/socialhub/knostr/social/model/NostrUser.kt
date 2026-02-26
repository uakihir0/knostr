package work.socialhub.knostr.social.model

import kotlin.js.JsExport

@JsExport
class NostrUser {
    lateinit var pubkey: String
    var npub: String = ""
    var name: String? = null
    var displayName: String? = null
    var about: String? = null
    var picture: String? = null
    var banner: String? = null
    var nip05: String? = null
    var website: String? = null
    var lud16: String? = null
    var followingCount: Int = 0
    var followersCount: Int = 0
    var isFollowing: Boolean = false
}
