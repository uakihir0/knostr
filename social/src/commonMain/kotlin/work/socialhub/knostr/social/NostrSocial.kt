package work.socialhub.knostr.social

import work.socialhub.knostr.Nostr
import work.socialhub.knostr.social.api.FeedResource
import work.socialhub.knostr.social.api.ReactionResource
import work.socialhub.knostr.social.api.SearchResource
import work.socialhub.knostr.social.api.UserResource
import kotlin.js.JsExport

@JsExport
interface NostrSocial {
    fun feed(): FeedResource
    fun users(): UserResource
    fun reactions(): ReactionResource
    fun search(): SearchResource
    fun nostr(): Nostr
}
