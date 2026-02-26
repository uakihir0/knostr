@file:Suppress("DEPRECATION")

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
import kotlinx.datetime.Clock

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

    override fun likeBlocking(eventId: String, authorPubkey: String): Response<NostrEvent> {
        return toBlocking { like(eventId, authorPubkey) }
    }

    override fun reactBlocking(eventId: String, authorPubkey: String, content: String): Response<NostrEvent> {
        return toBlocking { react(eventId, authorPubkey, content) }
    }

    override fun getReactionsBlocking(eventId: String): Response<List<NostrReaction>> {
        return toBlocking { getReactions(eventId) }
    }
}
