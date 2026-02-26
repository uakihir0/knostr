package work.socialhub.knostr.social.internal

import work.socialhub.knostr.Nostr
import work.socialhub.knostr.social.NostrSocial
import work.socialhub.knostr.social.api.FeedResource
import work.socialhub.knostr.social.api.ReactionResource
import work.socialhub.knostr.social.api.SearchResource
import work.socialhub.knostr.social.api.UserResource

class NostrSocialImpl(
    private val nostr: Nostr,
) : NostrSocial {

    private val feed: FeedResource = FeedResourceImpl(nostr)
    private val users: UserResource = UserResourceImpl(nostr)
    private val reactions: ReactionResource = ReactionResourceImpl(nostr)
    private val search: SearchResource = SearchResourceImpl(nostr)

    override fun feed() = feed
    override fun users() = users
    override fun reactions() = reactions
    override fun search() = search
    override fun nostr() = nostr
}
