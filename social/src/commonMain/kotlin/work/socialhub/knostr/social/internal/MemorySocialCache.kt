package work.socialhub.knostr.social.internal

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import work.socialhub.knostr.social.NostrSocialConfig
import work.socialhub.knostr.social.api.SocialCache
import work.socialhub.knostr.social.model.NostrNote
import work.socialhub.knostr.social.model.NostrNoteStats
import work.socialhub.knostr.social.model.NostrUser
import work.socialhub.knostr.social.model.SocialDataBatch
import work.socialhub.knostr.social.model.SocialDataRequest
import work.socialhub.knostr.util.toBlocking
import kotlin.time.Clock

class MemorySocialCache(
    private val config: NostrSocialConfig,
) : SocialCache {
    private data class Entry<T>(val value: T, val cachedAt: Long)

    private val users = mutableMapOf<String, Entry<NostrUser>>()
    private val notes = mutableMapOf<String, Entry<NostrNote>>()
    private val noteStats = mutableMapOf<String, Entry<NostrNoteStats>>()
    private val mutex = Mutex()

    override suspend fun get(request: SocialDataRequest): SocialDataBatch {
        val now = Clock.System.now().toEpochMilliseconds()
        return mutex.withLock {
            SocialDataBatch(
                users = if (config.cacheUserProfile) {
                    request.userPubkeys.distinct().mapNotNull { key ->
                        users[key]?.takeIf { now - it.cachedAt < config.userProfileCacheTtlMs }?.value
                    }
                } else {
                    listOf()
                },
                notes = request.noteIds.distinct().mapNotNull { key ->
                    notes[key]?.takeIf { now - it.cachedAt < config.noteCacheTtlMs }?.value
                },
                noteStats = request.noteStatsEventIds.distinct().mapNotNull { key ->
                    noteStats[key]?.takeIf { now - it.cachedAt < config.noteStatsCacheTtlMs }?.value
                },
            )
        }
    }

    override suspend fun put(batch: SocialDataBatch) {
        val now = Clock.System.now().toEpochMilliseconds()
        mutex.withLock {
            if (config.cacheUserProfile) {
                batch.users.forEach { users[it.pubkey] = Entry(it, now) }
            }
            batch.notes.forEach { notes[it.event.id] = Entry(it, now) }
            batch.noteStats.forEach { noteStats[it.eventId] = Entry(it, now) }
            evictIfNeeded(users)
            evictIfNeeded(notes)
            evictIfNeeded(noteStats)
        }
    }

    override fun getBlocking(request: SocialDataRequest): SocialDataBatch {
        return toBlocking { get(request) }
    }

    override fun putBlocking(batch: SocialDataBatch) {
        toBlocking { put(batch) }
    }

    internal suspend fun getStaleUsers(pubkeys: List<String>): List<NostrUser> {
        return mutex.withLock {
            pubkeys.distinct().mapNotNull { users[it]?.value }
        }
    }

    private fun <T> evictIfNeeded(cache: MutableMap<String, Entry<T>>) {
        if (cache.size <= MAX_ENTRIES) return
        val iterator = cache.iterator()
        repeat(EVICT_COUNT) {
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
    }

    private companion object {
        const val MAX_ENTRIES = 5_000
        const val EVICT_COUNT = 500
    }
}
