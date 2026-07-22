package work.socialhub.knostr.social.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
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
import work.socialhub.knostr.social.model.NostrUser
import work.socialhub.knostr.social.model.SocialDataBatch
import work.socialhub.knostr.social.model.SocialDataRequest
import work.socialhub.knostr.util.toBlocking

class EnrichmentResourceImpl(
    private val nostr: Nostr,
    private val cache: SocialCache,
    private val config: NostrSocialConfig,
) : EnrichmentResource {
    private data class StatsFetchResult(
        val events: List<NostrEvent> = listOf(),
        val isComplete: Boolean = false,
    )

    private data class FetchResult(
        val users: List<NostrUser> = listOf(),
        val notes: List<NostrNote> = listOf(),
        val stats: StatsFetchResult = StatsFetchResult(),
    )

    override var onUpdateCallback: ((SocialDataBatch) -> Unit)? = null

    private val pendingUsers = mutableSetOf<String>()
    private val pendingNotes = mutableSetOf<String>()
    private val pendingStats = mutableSetOf<String>()
    private val forceRefreshUsers = mutableSetOf<String>()
    private val forceRefreshNotes = mutableSetOf<String>()
    private val forceRefreshStats = mutableSetOf<String>()
    private val mutex = Mutex()
    private var scope: CoroutineScope? = newScope()

    override fun request(request: SocialDataRequest, forceRefresh: Boolean) {
        if (!config.deferredEnrichmentEnabled || request.isEmpty()) return
        val activeScope = scope ?: return
        activeScope.launch {
            val uniqueRequest = mutex.withLock {
                SocialDataRequest(
                    userPubkeys = request.userPubkeys.distinct().filter {
                        pendingUsers.add(it).also { added ->
                            if (forceRefresh && !added) forceRefreshUsers.add(it)
                        }
                    },
                    noteIds = request.noteIds.distinct().filter {
                        pendingNotes.add(it).also { added ->
                            if (forceRefresh && !added) forceRefreshNotes.add(it)
                        }
                    },
                    noteStatsEventIds = request.noteStatsEventIds.distinct().filter {
                        pendingStats.add(it).also { added ->
                            if (forceRefresh && !added) forceRefreshStats.add(it)
                        }
                    },
                )
            }
            if (uniqueRequest.isEmpty()) return@launch

            var released = false
            try {
                process(uniqueRequest, forceRefresh)
                released = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Unexpected failures must not escape the shared enrichment scope.
            } finally {
                if (!released) {
                    mutex.withLock {
                        release(uniqueRequest)
                    }
                }
            }
        }
    }

    internal fun requestMissing(request: SocialDataRequest) {
        request(request, forceRefresh = false)
    }

    override suspend fun cancelPending() {
        val pendingJobs = scope
            ?.coroutineContext
            ?.get(kotlinx.coroutines.Job)
            ?.children
            ?.toList()
            .orEmpty()
        pendingJobs.forEach { it.cancel() }
        pendingJobs.joinAll()
        mutex.withLock {
            pendingUsers.clear()
            pendingNotes.clear()
            pendingStats.clear()
            forceRefreshUsers.clear()
            forceRefreshNotes.clear()
            forceRefreshStats.clear()
        }
    }

    override fun cancelPendingBlocking() {
        toBlocking { cancelPending() }
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
        val accumulatedStatsEvents = mutableMapOf<String, NostrEvent>()

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

            val fetched = fetch(
                userPubkeys = remainingUsers.toList(),
                noteIds = remainingNotes.toList(),
                statsEventIds = remainingStats.toList(),
            )
            fetched.stats.events.forEach { accumulatedStatsEvents[it.id] = it }
            val stats = if (fetched.stats.isComplete) {
                remainingStats.map { eventId ->
                    SocialStats.calculate(eventId, accumulatedStatsEvents.values)
                }
            } else {
                listOf()
            }
            val batch = SocialDataBatch(
                users = fetched.users,
                notes = fetched.notes,
                noteStats = stats,
            )
            if (!batch.isEmpty()) {
                emit(batch, writeToCache = true)
                remainingUsers.removeAll(batch.users.map { user -> user.pubkey }.toSet())
                remainingNotes.removeAll(batch.notes.map { note -> note.event.id }.toSet())
                remainingStats.removeAll(batch.noteStats.map { stats -> stats.eventId }.toSet())
                val noteAuthors = batch.notes.map { it.event.pubkey }.distinct()
                val quotedNoteIds = batch.notes.mapNotNull { note ->
                    note.quotedEventId?.takeIf { note.quotedNote == null }
                }.distinct()
                if (noteAuthors.isNotEmpty() || quotedNoteIds.isNotEmpty()) {
                    requestMissing(
                        SocialDataRequest(
                            userPubkeys = noteAuthors,
                            noteIds = quotedNoteIds,
                        ),
                    )
                }
            }
            retryDelay = (retryDelay * multiplier).toLong()
        }
    }

    private suspend fun process(initialRequest: SocialDataRequest, forceRefresh: Boolean) {
        var request = initialRequest
        var shouldForceRefresh = forceRefresh
        while (true) {
            resolve(request, shouldForceRefresh)
            val upgrade = mutex.withLock {
                val next = SocialDataRequest(
                    userPubkeys = initialRequest.userPubkeys.filter { forceRefreshUsers.remove(it) },
                    noteIds = initialRequest.noteIds.filter { forceRefreshNotes.remove(it) },
                    noteStatsEventIds = initialRequest.noteStatsEventIds.filter { forceRefreshStats.remove(it) },
                )
                if (next.isEmpty()) {
                    release(initialRequest)
                    null
                } else {
                    next
                }
            } ?: return
            request = upgrade
            shouldForceRefresh = true
        }
    }

    private fun release(request: SocialDataRequest) {
        pendingUsers.removeAll(request.userPubkeys.toSet())
        pendingNotes.removeAll(request.noteIds.toSet())
        pendingStats.removeAll(request.noteStatsEventIds.toSet())
        forceRefreshUsers.removeAll(request.userPubkeys.toSet())
        forceRefreshNotes.removeAll(request.noteIds.toSet())
        forceRefreshStats.removeAll(request.noteStatsEventIds.toSet())
    }

    private suspend fun fetch(
        userPubkeys: List<String>,
        noteIds: List<String>,
        statsEventIds: List<String>,
    ): FetchResult = supervisorScope {
        val users = async { fetchUsers(userPubkeys) }
        val notes = async { fetchNotes(noteIds) }
        val stats = async { fetchStats(statsEventIds) }
        FetchResult(
            users = users.await(),
            notes = notes.await(),
            stats = stats.await(),
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
        } catch (e: CancellationException) {
            throw e
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
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            listOf()
        }
    }

    private suspend fun fetchStats(eventIds: List<String>): StatsFetchResult {
        if (eventIds.isEmpty()) return StatsFetchResult(isComplete = true)
        return try {
            val response = nostr.events().queryEvents(SocialStats.filters(eventIds))
            StatsFetchResult(
                events = response.data,
                isComplete = response.isComplete,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            StatsFetchResult()
        }
    }

    private suspend fun cacheGet(request: SocialDataRequest): SocialDataBatch {
        return try {
            cache.get(request)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            SocialDataBatch()
        }
    }

    private suspend fun emit(batch: SocialDataBatch, writeToCache: Boolean) {
        if (batch.isEmpty()) return
        if (writeToCache) {
            try {
                cache.put(batch)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Cache failures must not suppress successfully resolved data.
            }
        }
        try {
            onUpdateCallback?.invoke(batch)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Client callback failures do not stop remaining enrichment work.
        }
    }

    private fun newScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
