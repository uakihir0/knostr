package work.socialhub.knostr.social.internal

import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.signing.NostrSigner
import work.socialhub.knostr.social.api.BookmarkResource
import work.socialhub.knostr.util.toBlocking
import kotlin.time.Clock

class BookmarkResourceImpl(
    private val nostr: Nostr,
) : BookmarkResource {

    override suspend fun bookmark(eventId: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to bookmark")

        val currentTags = getBookmarkTags()
        val tags = currentTags.toMutableList()
        if (tags.none { it.size >= 2 && it[0] == "e" && it[1] == eventId }) {
            tags.add(listOf("e", eventId))
        }

        return publishBookmarkList(signer, tags)
    }

    override suspend fun unbookmark(eventId: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to unbookmark")

        val currentTags = getBookmarkTags()
        val tags = currentTags.filter { !(it.size >= 2 && it[0] == "e" && it[1] == eventId) }

        return publishBookmarkList(signer, tags)
    }

    override suspend fun getBookmarks(): Response<List<String>> {
        val tags = getBookmarkTags()
        val eventIds = tags
            .filter { it.size >= 2 && it[0] == "e" }
            .map { it[1] }
        return Response(eventIds)
    }

    private suspend fun getBookmarkTags(): List<List<String>> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to get bookmarks")

        val filter = NostrFilter(
            authors = listOf(signer.getPublicKey()),
            kinds = listOf(EventKind.BOOKMARK_LIST),
            limit = 1,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        return response.data.firstOrNull()?.tags ?: listOf()
    }

    private suspend fun publishBookmarkList(
        signer: NostrSigner,
        tags: List<List<String>>,
    ): Response<NostrEvent> {
        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.BOOKMARK_LIST,
            tags = tags,
            content = "",
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override fun bookmarkBlocking(eventId: String): Response<NostrEvent> {
        return toBlocking { bookmark(eventId) }
    }

    override fun unbookmarkBlocking(eventId: String): Response<NostrEvent> {
        return toBlocking { unbookmark(eventId) }
    }

    override fun getBookmarksBlocking(): Response<List<String>> {
        return toBlocking { getBookmarks() }
    }
}
