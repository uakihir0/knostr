package work.socialhub.knostr

import work.socialhub.knostr.social.NostrSocialFactory
import kotlin.js.JsExport

@JsExport
class KnostrFactory {

    fun nostr(config: NostrConfig) = NostrFactory.instance(config)

    fun social(nostr: Nostr) = NostrSocialFactory.instance(nostr)

    companion object {

        /**
         * 参照を行わないとフレームワーク内でオミットされるため。
         * Because it is omitted in the framework if no reference is made.
         */
        fun getNostrFactory() = NostrFactory
        fun getNostrSocialFactory() = NostrSocialFactory
    }
}
