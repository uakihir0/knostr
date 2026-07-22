package work.socialhub.knostr.social.internal

import kotlinx.coroutines.CancellationException
import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.social.NostrSocialConfig
import work.socialhub.knostr.social.api.SearchResource
import work.socialhub.knostr.social.api.SocialCache
import work.socialhub.knostr.social.model.NostrNote
import work.socialhub.knostr.social.model.NostrUser
import work.socialhub.knostr.social.model.SocialDataBatch
import work.socialhub.knostr.social.model.SocialDataRequest
import work.socialhub.knostr.util.toBlocking

class SearchResourceImpl(
    private val nostr: Nostr,
    config: NostrSocialConfig = NostrSocialConfig(),
    private val socialCache: SocialCache = MemorySocialCache(config),
    private val enrichment: EnrichmentResourceImpl = EnrichmentResourceImpl(nostr, socialCache, config),
) : SearchResource {

    override suspend fun searchNotes(query: String, limit: Int): Response<List<NostrNote>> {
        val filter = NostrFilter(
            kinds = listOf(EventKind.TEXT_NOTE),
            search = query,
            limit = limit,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val notes = response.data.map { SocialMapper.toNote(it) }
        cachePut(SocialDataBatch(notes = notes))
        enrichNotes(notes)
        return response.withData(notes)
    }

    override suspend fun searchUsers(query: String, limit: Int): Response<List<NostrUser>> {
        val filter = NostrFilter(
            kinds = listOf(EventKind.METADATA),
            search = query,
            limit = limit,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val users = response.data.map { SocialMapper.toUser(it) }
        cachePut(SocialDataBatch(users = users))
        return response.withData(users)
    }

    private suspend fun enrichNotes(notes: List<NostrNote>) {
        if (notes.isEmpty()) return
        val pubkeys = notes.map { it.event.pubkey }.distinct()
        val quoteIds = notes.mapNotNull { it.quotedEventId }.distinct()
        val eventIds = notes.map { it.event.id }
        val cached = try {
            socialCache.get(
                SocialDataRequest(
                    userPubkeys = pubkeys,
                    noteIds = quoteIds,
                    noteStatsEventIds = eventIds,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            SocialDataBatch()
        }
        val users = cached.users.associateBy { it.pubkey }
        val quotes = cached.notes.associateBy { it.event.id }
        val stats = cached.noteStats.associateBy { it.eventId }
        notes.forEach { note ->
            note.author = users[note.event.pubkey]
            note.quotedNote = note.quotedEventId?.let { quotes[it] }
            stats[note.event.id]?.let {
                note.likeCount = it.likeCount
                note.replyCount = it.replyCount
                note.repostCount = it.repostCount
            }
        }
        enrichment.requestMissing(
            SocialDataRequest(
                userPubkeys = pubkeys.filter { it !in users },
                noteIds = quoteIds.filter { it !in quotes },
                noteStatsEventIds = eventIds.filter { it !in stats },
            )
        )
    }

    private suspend fun cachePut(batch: SocialDataBatch) {
        if (batch.isEmpty()) return
        try {
            socialCache.put(batch)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Cache failures must not fail search.
        }
    }

    override fun searchNotesBlocking(query: String, limit: Int): Response<List<NostrNote>> {
        return toBlocking { searchNotes(query, limit) }
    }

    override fun searchUsersBlocking(query: String, limit: Int): Response<List<NostrUser>> {
        return toBlocking { searchUsers(query, limit) }
    }
}
