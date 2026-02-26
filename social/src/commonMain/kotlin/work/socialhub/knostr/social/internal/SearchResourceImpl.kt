package work.socialhub.knostr.social.internal

import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.social.api.SearchResource
import work.socialhub.knostr.social.model.NostrNote
import work.socialhub.knostr.social.model.NostrUser
import work.socialhub.knostr.util.toBlocking

class SearchResourceImpl(
    private val nostr: Nostr,
) : SearchResource {

    override suspend fun searchNotes(query: String, limit: Int): Response<List<NostrNote>> {
        val filter = NostrFilter(
            kinds = listOf(EventKind.TEXT_NOTE),
            search = query,
            limit = limit,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val notes = response.data.map { SocialMapper.toNote(it) }
        return Response(notes)
    }

    override suspend fun searchUsers(query: String, limit: Int): Response<List<NostrUser>> {
        val filter = NostrFilter(
            kinds = listOf(EventKind.METADATA),
            search = query,
            limit = limit,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val users = response.data.map { SocialMapper.toUser(it) }
        return Response(users)
    }

    override fun searchNotesBlocking(query: String, limit: Int): Response<List<NostrNote>> {
        return toBlocking { searchNotes(query, limit) }
    }

    override fun searchUsersBlocking(query: String, limit: Int): Response<List<NostrUser>> {
        return toBlocking { searchUsers(query, limit) }
    }
}
