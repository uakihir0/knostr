package work.socialhub.knostr.social.internal

import work.socialhub.knostr.Nostr
import work.socialhub.knostr.social.NostrSocial
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

class NostrSocialImpl(
    private val nostr: Nostr,
) : NostrSocial {

    private val feed: FeedResource = FeedResourceImpl(nostr)
    private val users: UserResource = UserResourceImpl(nostr)
    private val reactions: ReactionResource = ReactionResourceImpl(nostr)
    private val search: SearchResource = SearchResourceImpl(nostr)
    private val media: MediaResource = MediaResourceImpl(nostr)
    private val zaps: ZapResource = ZapResourceImpl(nostr)
    private val mutes: MuteResource = MuteResourceImpl(nostr)
    private val messages: MessageResource = MessageResourceImpl(nostr)
    private val relayList: RelayListResource = RelayListResourceImpl(nostr)
    private val bookmarks: BookmarkResource = BookmarkResourceImpl(nostr)
    private val pins: PinResource = PinResourceImpl(nostr)
    private val interests: InterestResource = InterestResourceImpl(nostr)
    private val polls: PollResource = PollResourceImpl(nostr)

    override fun feed() = feed
    override fun users() = users
    override fun reactions() = reactions
    override fun search() = search
    override fun media() = media
    override fun zaps() = zaps
    override fun mutes() = mutes
    override fun messages() = messages
    override fun relayList() = relayList
    override fun bookmarks() = bookmarks
    override fun pins() = pins
    override fun interests() = interests
    override fun polls() = polls
    override fun nostr() = nostr
}
