package work.socialhub.knostr.social

import work.socialhub.knostr.Nostr
import work.socialhub.knostr.social.internal.NostrSocialImpl
import kotlin.js.JsExport

@JsExport
object NostrSocialFactory {

    fun instance(nostr: Nostr): NostrSocial {
        return NostrSocialImpl(nostr)
    }
}
