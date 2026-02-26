@file:Suppress("DEPRECATION")

package work.socialhub.knostr.social.internal

import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.social.api.FeedResource
import work.socialhub.knostr.social.model.NostrNote
import work.socialhub.knostr.util.toBlocking
import kotlinx.datetime.Clock

class FeedResourceImpl(
    private val nostr: Nostr,
) : FeedResource {

    override suspend fun getHomeFeed(since: Long?, until: Long?, limit: Int): Response<List<NostrNote>> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to get home feed")

        // First, get the follow list (kind:3)
        val followFilter = NostrFilter(
            authors = listOf(signer.getPublicKey()),
            kinds = listOf(EventKind.FOLLOW_LIST),
            limit = 1,
        )
        val followResponse = nostr.events().queryEvents(listOf(followFilter))
        val followPubkeys = followResponse.data
            .firstOrNull()
            ?.let { SocialMapper.toFollowList(it) }
            ?: listOf()

        if (followPubkeys.isEmpty()) {
            return Response(listOf())
        }

        // Then, get posts from followed users
        val feedFilter = NostrFilter(
            authors = followPubkeys,
            kinds = listOf(EventKind.TEXT_NOTE),
            since = since,
            until = until,
            limit = limit,
        )
        val feedResponse = nostr.events().queryEvents(listOf(feedFilter))
        val notes = feedResponse.data.map { SocialMapper.toNote(it) }

        return Response(notes)
    }

    override suspend fun post(content: String, tags: List<List<String>>): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to post")

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.TEXT_NOTE,
            tags = tags,
            content = content,
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun reply(content: String, replyToEventId: String, rootEventId: String?): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to reply")

        // NIP-10: build e-tags with root/reply markers
        val tags = mutableListOf<List<String>>()
        val effectiveRootId = rootEventId ?: replyToEventId
        tags.add(listOf("e", effectiveRootId, "", "root"))
        if (effectiveRootId != replyToEventId) {
            tags.add(listOf("e", replyToEventId, "", "reply"))
        }

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.TEXT_NOTE,
            tags = tags,
            content = content,
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun repost(eventId: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to repost")

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.REPOST,
            tags = listOf(listOf("e", eventId)),
            content = "",
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun delete(eventId: String, reason: String): Response<Boolean> {
        return nostr.events().deleteEvent(eventId, reason)
    }

    override fun getHomeFeedBlocking(since: Long?, until: Long?, limit: Int): Response<List<NostrNote>> {
        return toBlocking { getHomeFeed(since, until, limit) }
    }

    override fun postBlocking(content: String, tags: List<List<String>>): Response<NostrEvent> {
        return toBlocking { post(content, tags) }
    }

    override fun replyBlocking(content: String, replyToEventId: String, rootEventId: String?): Response<NostrEvent> {
        return toBlocking { reply(content, replyToEventId, rootEventId) }
    }

    override fun repostBlocking(eventId: String): Response<NostrEvent> {
        return toBlocking { repost(eventId) }
    }

    override fun deleteBlocking(eventId: String, reason: String): Response<Boolean> {
        return toBlocking { delete(eventId, reason) }
    }
}
