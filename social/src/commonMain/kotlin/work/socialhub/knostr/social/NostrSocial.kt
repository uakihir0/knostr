package work.socialhub.knostr.social

import work.socialhub.knostr.Nostr
import work.socialhub.knostr.social.api.FeedResource
import work.socialhub.knostr.social.api.MediaResource
import work.socialhub.knostr.social.api.MessageResource
import work.socialhub.knostr.social.api.MuteResource
import work.socialhub.knostr.social.api.ReactionResource
import work.socialhub.knostr.social.api.SearchResource
import work.socialhub.knostr.social.api.UserResource
import work.socialhub.knostr.social.api.BookmarkResource
import work.socialhub.knostr.social.api.InterestResource
import work.socialhub.knostr.social.api.PinResource
import work.socialhub.knostr.social.api.PollResource
import work.socialhub.knostr.social.api.RelayListResource
import work.socialhub.knostr.social.api.ZapResource
import kotlin.js.JsExport

@JsExport
interface NostrSocial {
    fun feed(): FeedResource
    fun users(): UserResource
    fun reactions(): ReactionResource
    fun search(): SearchResource
    fun media(): MediaResource
    fun zaps(): ZapResource
    fun mutes(): MuteResource
    fun messages(): MessageResource
    fun relayList(): RelayListResource
    fun bookmarks(): BookmarkResource
    fun pins(): PinResource
    fun interests(): InterestResource
    fun polls(): PollResource
    fun nostr(): Nostr
}
