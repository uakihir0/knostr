package work.socialhub.knostr.social.internal

import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.social.api.ReactionResource
import work.socialhub.knostr.social.model.NostrReaction
import work.socialhub.knostr.util.toBlocking
import kotlin.time.Clock

class ReactionResourceImpl(
    private val nostr: Nostr,
) : ReactionResource {

    override suspend fun like(eventId: String, authorPubkey: String): Response<NostrEvent> {
        return react(eventId, authorPubkey, "+")
    }

    override suspend fun react(eventId: String, authorPubkey: String, content: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to react")

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.REACTION,
            tags = listOf(
                listOf("e", eventId),
                listOf("p", authorPubkey),
            ),
            content = content,
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun getReactions(eventId: String): Response<List<NostrReaction>> {
        val filter = NostrFilter(
            kinds = listOf(EventKind.REACTION),
            eTags = listOf(eventId),
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val reactions = response.data.map { SocialMapper.toReaction(it) }
        return Response(reactions)
    }

    override suspend fun unlike(eventId: String): Response<Boolean> {
        return deleteOwnReaction(eventId, "+")
    }

    override suspend fun unreact(eventId: String, content: String?): Response<Boolean> {
        return deleteOwnReaction(eventId, content)
    }

    override suspend fun reactWithEmoji(eventId: String, authorPubkey: String, shortcode: String, emojiUrl: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to react")

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.REACTION,
            tags = listOf(
                listOf("e", eventId),
                listOf("p", authorPubkey),
                listOf("emoji", shortcode, emojiUrl),
            ),
            content = ":$shortcode:",
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun getUserReactions(pubkey: String, since: Long?, until: Long?, limit: Int): Response<List<NostrReaction>> {
        val filter = NostrFilter(
            authors = listOf(pubkey),
            kinds = listOf(EventKind.REACTION),
            since = since,
            until = until,
            limit = limit,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val reactions = response.data.map { SocialMapper.toReaction(it) }
        return Response(reactions)
    }

    /**
     * Find own reaction event for a target eventId and delete it via kind:5.
     * @param content If non-null, match reaction content (e.g. "+" for like). If null, delete any reaction.
     */
    private suspend fun deleteOwnReaction(eventId: String, content: String?): Response<Boolean> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to remove reaction")

        // Find own reaction for this event
        val filter = NostrFilter(
            authors = listOf(signer.getPublicKey()),
            kinds = listOf(EventKind.REACTION),
            eTags = listOf(eventId),
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val reactionEvent = if (content != null) {
            response.data.find { it.content == content }
        } else {
            response.data.firstOrNull()
        } ?: throw NostrException("No matching reaction found for event: $eventId")

        // Delete via kind:5
        return nostr.events().deleteEvent(reactionEvent.id)
    }

    override fun likeBlocking(eventId: String, authorPubkey: String): Response<NostrEvent> {
        return toBlocking { like(eventId, authorPubkey) }
    }

    override fun reactBlocking(eventId: String, authorPubkey: String, content: String): Response<NostrEvent> {
        return toBlocking { react(eventId, authorPubkey, content) }
    }

    override fun getReactionsBlocking(eventId: String): Response<List<NostrReaction>> {
        return toBlocking { getReactions(eventId) }
    }

    override fun unlikeBlocking(eventId: String): Response<Boolean> {
        return toBlocking { unlike(eventId) }
    }

    override fun unreactBlocking(eventId: String, content: String?): Response<Boolean> {
        return toBlocking { unreact(eventId, content) }
    }

    override fun reactWithEmojiBlocking(eventId: String, authorPubkey: String, shortcode: String, emojiUrl: String): Response<NostrEvent> {
        return toBlocking { reactWithEmoji(eventId, authorPubkey, shortcode, emojiUrl) }
    }

    override fun getUserReactionsBlocking(pubkey: String, since: Long?, until: Long?, limit: Int): Response<List<NostrReaction>> {
        return toBlocking { getUserReactions(pubkey, since, until, limit) }
    }
}
