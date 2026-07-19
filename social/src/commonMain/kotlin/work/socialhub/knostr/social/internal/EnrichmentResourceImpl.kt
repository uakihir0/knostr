package work.socialhub.knostr.social.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.social.NostrSocialConfig
import work.socialhub.knostr.social.api.EnrichmentResource
import work.socialhub.knostr.social.api.SocialCache
import work.socialhub.knostr.social.model.NostrNote
import work.socialhub.knostr.social.model.NostrNoteStats
import work.socialhub.knostr.social.model.NostrUser
import work.socialhub.knostr.social.model.SocialDataBatch
import work.socialhub.knostr.social.model.SocialDataRequest

class EnrichmentResourceImpl(
    private val nostr: Nostr,
    private val cache: SocialCache,
    private val config: NostrSocialConfig,
) : EnrichmentResource {
    override var onUpdateCallback: ((SocialDataBatch) -> Unit)? = null

    private val pendingUsers = mutableSetOf<String>()
    private val pendingNotes = mutableSetOf<String>()
    private val pendingStats = mutableSetOf<String>()
    private val mutex = Mutex()
    private var scope: CoroutineScope? = newScope()

    override fun request(request: SocialDataRequest, forceRefresh: Boolean) {
        if (!config.deferredEnrichmentEnabled || request.isEmpty()) return
        val activeScope = scope ?: return
        activeScope.launch {
            val uniqueRequest = mutex.withLock {
                SocialDataRequest(
                    userPubkeys = request.userPubkeys.distinct().filter { pendingUsers.add(it) },
                    noteIds = request.noteIds.distinct().filter { pendingNotes.add(it) },
                    noteStatsEventIds = request.noteStatsEventIds.distinct().filter { pendingStats.add(it) },
                )
            }
            if (uniqueRequest.isEmpty()) return@launch

            try {
                resolve(uniqueRequest, forceRefresh)
            } finally {
                mutex.withLock {
                    pendingUsers.removeAll(uniqueRequest.userPubkeys.toSet())
                    pendingNotes.removeAll(uniqueRequest.noteIds.toSet())
                    pendingStats.removeAll(uniqueRequest.noteStatsEventIds.toSet())
                }
            }
        }
    }

    internal fun requestMissing(request: SocialDataRequest) {
        request(request, forceRefresh = false)
    }

    override suspend fun cancelPending() {
        scope?.coroutineContext?.cancelChildren()
        mutex.withLock {
            pendingUsers.clear()
            pendingNotes.clear()
            pendingStats.clear()
        }
    }

    override fun close() {
        scope?.cancel()
        scope = null
        onUpdateCallback = null
    }

    private suspend fun resolve(request: SocialDataRequest, forceRefresh: Boolean) {
        val remainingUsers = request.userPubkeys.toMutableSet()
        val remainingNotes = request.noteIds.toMutableSet()
        val remainingStats = request.noteStatsEventIds.toMutableSet()

        if (!forceRefresh) {
            val cached = cacheGet(request)
            if (!cached.isEmpty()) {
                emit(cached, writeToCache = false)
                remainingUsers.removeAll(cached.users.map { it.pubkey }.toSet())
                remainingNotes.removeAll(cached.notes.map { it.event.id }.toSet())
                remainingStats.removeAll(cached.noteStats.map { it.eventId }.toSet())
            }
        }

        var retryDelay = config.deferredEnrichmentInitialDelayMs.coerceAtLeast(0)
        val attempts = config.deferredEnrichmentMaxAttempts.coerceAtLeast(0)
        val multiplier = config.deferredEnrichmentBackoffMultiplier.coerceAtLeast(1.0)

        repeat(attempts) {
            if (remainingUsers.isEmpty() && remainingNotes.isEmpty() && remainingStats.isEmpty()) {
                return
            }
            if (retryDelay > 0) delay(retryDelay)

            val batch = fetch(
                userPubkeys = remainingUsers.toList(),
                noteIds = remainingNotes.toList(),
                statsEventIds = remainingStats.toList(),
            )
            if (!batch.isEmpty()) {
                emit(batch, writeToCache = true)
                remainingUsers.removeAll(batch.users.map { user -> user.pubkey }.toSet())
                remainingNotes.removeAll(batch.notes.map { note -> note.event.id }.toSet())
                remainingStats.removeAll(batch.noteStats.map { stats -> stats.eventId }.toSet())
            }
            retryDelay = (retryDelay * multiplier).toLong()
        }
    }

    private suspend fun fetch(
        userPubkeys: List<String>,
        noteIds: List<String>,
        statsEventIds: List<String>,
    ): SocialDataBatch = supervisorScope {
        val users = async { fetchUsers(userPubkeys) }
        val notes = async { fetchNotes(noteIds) }
        val stats = async { fetchStats(statsEventIds) }
        SocialDataBatch(
            users = users.await(),
            notes = notes.await(),
            noteStats = stats.await(),
        )
    }

    private suspend fun fetchUsers(pubkeys: List<String>): List<NostrUser> {
        if (pubkeys.isEmpty()) return listOf()
        return try {
            val response = nostr.events().queryEvents(
                listOf(NostrFilter(authors = pubkeys, kinds = listOf(EventKind.METADATA)))
            )
            response.data
                .sortedByDescending { it.createdAt }
                .distinctBy { it.pubkey }
                .map { SocialMapper.toUser(it) }
        } catch (_: Exception) {
            listOf()
        }
    }

    private suspend fun fetchNotes(eventIds: List<String>): List<NostrNote> {
        if (eventIds.isEmpty()) return listOf()
        return try {
            val response = nostr.events().queryEvents(
                listOf(
                    NostrFilter(
                        ids = eventIds,
                        kinds = listOf(EventKind.TEXT_NOTE),
                    )
                )
            )
            response.data.distinctBy { it.id }.map { SocialMapper.toNote(it) }
        } catch (_: Exception) {
            listOf()
        }
    }

    private suspend fun fetchStats(eventIds: List<String>): List<NostrNoteStats> {
        if (eventIds.isEmpty()) return listOf()
        return try {
            val response = nostr.events().queryEvents(
                listOf(
                    NostrFilter(
                        kinds = listOf(
                            EventKind.TEXT_NOTE,
                            EventKind.REPOST,
                            EventKind.GENERIC_REPOST,
                            EventKind.REACTION,
                        ),
                        eTags = eventIds,
                    )
                )
            )
            if (!response.isComplete) return listOf()
            eventIds.map { eventId -> calculateStats(eventId, response.data) }
        } catch (_: Exception) {
            listOf()
        }
    }

    private fun calculateStats(eventId: String, events: List<NostrEvent>): NostrNoteStats {
        return NostrNoteStats(
            eventId = eventId,
            likeCount = SocialMapper.countLikes(
                events.filter {
                    it.kind == EventKind.REACTION && SocialMapper.getReactionTarget(it) == eventId
                }
            ),
            replyCount = events.count {
                it.kind == EventKind.TEXT_NOTE && findReplyParent(it) == eventId
            },
            repostCount = events.count {
                (it.kind == EventKind.REPOST || it.kind == EventKind.GENERIC_REPOST) &&
                    it.tags.any { tag -> tag.size >= 2 && tag[0] == "e" && tag[1] == eventId }
            },
        )
    }

    private fun findReplyParent(event: NostrEvent): String? {
        val eTags = event.tags.filter { it.size >= 2 && it[0] == "e" }
        if (eTags.isEmpty()) return null
        eTags.firstOrNull { it.size >= 4 && it[3] == "reply" }?.let { return it[1] }
        return if (eTags.size == 1) eTags[0][1] else eTags.last()[1]
    }

    private suspend fun cacheGet(request: SocialDataRequest): SocialDataBatch {
        return try {
            cache.get(request)
        } catch (_: Exception) {
            SocialDataBatch()
        }
    }

    private suspend fun emit(batch: SocialDataBatch, writeToCache: Boolean) {
        if (batch.isEmpty()) return
        if (writeToCache) {
            try {
                cache.put(batch)
            } catch (_: Exception) {
                // Cache failures must not suppress successfully resolved data.
            }
        }
        try {
            onUpdateCallback?.invoke(batch)
        } catch (_: Exception) {
            // Client callback failures do not stop remaining enrichment work.
        }
    }

    private fun newScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
