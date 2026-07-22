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
        val newestUsers = response.data
            .sortedByDescending { it.createdAt }
            .distinctBy { it.pubkey }
            .map { SocialMapper.toUser(it) }
        if (response.isComplete) {
            cachePut(SocialDataBatch(users = newestUsers))
        } else {
            enrichment.request(
                SocialDataRequest(userPubkeys = newestUsers.map { it.pubkey }),
                forceRefresh = true,
            )
        }
        return response.withData(users)
    }

    private suspend fun enrichNotes(notes: List<NostrNote>) {
        if (notes.isEmpty()) return
        val topLevelPubkeys = notes.map { it.event.pubkey }.distinct()
        val quoteIds = notes.mapNotNull { it.quotedEventId }.distinct()
        val eventIds = notes.map { it.event.id }
        val cached = cacheGet(
            SocialDataRequest(
                userPubkeys = topLevelPubkeys,
                noteIds = quoteIds,
                noteStatsEventIds = eventIds,
            )
        )
        val users = cached.users.associateByTo(mutableMapOf()) { it.pubkey }
        val quotes = cached.notes.associateBy { it.event.id }
        val stats = cached.noteStats.associateBy { it.eventId }
        notes.forEach { note ->
            note.quotedNote = note.quotedEventId?.let { quotes[it] }
            stats[note.event.id]?.let {
                note.likeCount = it.likeCount
                note.replyCount = it.replyCount
                note.repostCount = it.repostCount
            }
        }

        val noteGraph = mutableListOf<NostrNote>()
        val seenNoteIds = mutableSetOf<String>()
        fun collect(note: NostrNote?) {
            if (note == null || !seenNoteIds.add(note.event.id)) return
            noteGraph.add(note)
            collect(note.quotedNote)
        }
        notes.forEach { collect(it) }

        val uncachedPubkeys = noteGraph
            .filter { it.author == null }
            .map { it.event.pubkey }
            .distinct()
            .filter { it !in users }
        if (uncachedPubkeys.isNotEmpty()) {
            cacheGet(SocialDataRequest(userPubkeys = uncachedPubkeys))
                .users
                .forEach { users[it.pubkey] = it }
        }
        noteGraph.forEach { note ->
            users[note.event.pubkey]?.let { note.author = it }
        }

        val missingPubkeys = noteGraph
            .filter { it.author == null }
            .map { it.event.pubkey }
            .distinct()
        val missingQuoteIds = noteGraph
            .mapNotNull { note -> note.quotedEventId?.takeIf { note.quotedNote == null } }
            .distinct()
        enrichment.requestMissing(
            SocialDataRequest(
                userPubkeys = missingPubkeys,
                noteIds = missingQuoteIds,
                noteStatsEventIds = eventIds.filter { it !in stats },
            )
        )
    }

    private suspend fun cacheGet(request: SocialDataRequest): SocialDataBatch {
        return try {
            socialCache.get(request)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            SocialDataBatch()
        }
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
