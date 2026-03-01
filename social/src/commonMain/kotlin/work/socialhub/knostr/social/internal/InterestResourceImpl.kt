package work.socialhub.knostr.social.internal

import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.signing.NostrSigner
import work.socialhub.knostr.social.api.InterestResource
import work.socialhub.knostr.util.toBlocking
import kotlin.time.Clock

class InterestResourceImpl(
    private val nostr: Nostr,
) : InterestResource {

    override suspend fun followHashtag(hashtag: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to follow hashtag")

        val currentTags = getInterestTags()
        val tags = currentTags.toMutableList()
        val normalized = hashtag.lowercase()
        if (tags.none { it.size >= 2 && it[0] == "t" && it[1] == normalized }) {
            tags.add(listOf("t", normalized))
        }

        return publishInterestList(signer, tags)
    }

    override suspend fun unfollowHashtag(hashtag: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to unfollow hashtag")

        val currentTags = getInterestTags()
        val normalized = hashtag.lowercase()
        val tags = currentTags.filter { !(it.size >= 2 && it[0] == "t" && it[1] == normalized) }

        return publishInterestList(signer, tags)
    }

    override suspend fun getFollowedHashtags(): Response<List<String>> {
        val tags = getInterestTags()
        val hashtags = tags
            .filter { it.size >= 2 && it[0] == "t" }
            .map { it[1] }
        return Response(hashtags)
    }

    private suspend fun getInterestTags(): List<List<String>> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to get interest list")

        val filter = NostrFilter(
            authors = listOf(signer.getPublicKey()),
            kinds = listOf(EventKind.INTEREST_LIST),
            limit = 1,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        return response.data.firstOrNull()?.tags ?: listOf()
    }

    private suspend fun publishInterestList(
        signer: NostrSigner,
        tags: List<List<String>>,
    ): Response<NostrEvent> {
        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.INTEREST_LIST,
            tags = tags,
            content = "",
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override fun followHashtagBlocking(hashtag: String): Response<NostrEvent> {
        return toBlocking { followHashtag(hashtag) }
    }

    override fun unfollowHashtagBlocking(hashtag: String): Response<NostrEvent> {
        return toBlocking { unfollowHashtag(hashtag) }
    }

    override fun getFollowedHashtagsBlocking(): Response<List<String>> {
        return toBlocking { getFollowedHashtags() }
    }
}
