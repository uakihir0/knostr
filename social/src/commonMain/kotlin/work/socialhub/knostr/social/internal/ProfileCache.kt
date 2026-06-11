package work.socialhub.knostr.social.internal

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import work.socialhub.knostr.social.NostrSocialConfig
import work.socialhub.knostr.social.model.NostrUser
import kotlin.time.Clock

/**
 * Shared profile cache used across FeedResource, UserResource, and streams.
 * Thread-safe via Mutex; entries expire based on config TTL.
 */
class ProfileCache(
    private val config: NostrSocialConfig,
) {
    private data class Entry(val user: NostrUser, val cachedAt: Long)

    private val cache = mutableMapOf<String, Entry>()
    private val mutex = Mutex()

    private companion object {
        const val MAX_ENTRIES = 5_000
    }

    suspend fun get(pubkey: String): NostrUser? {
        if (!config.cacheUserProfile) return null
        mutex.withLock {
            val entry = cache[pubkey] ?: return null
            val now = Clock.System.now().toEpochMilliseconds()
            return if ((now - entry.cachedAt) < config.userProfileCacheTtlMs) entry.user else null
        }
    }

    suspend fun getStale(pubkey: String): NostrUser? {
        mutex.withLock {
            return cache[pubkey]?.user
        }
    }

    suspend fun put(pubkey: String, user: NostrUser) {
        if (!config.cacheUserProfile) return
        mutex.withLock {
            evictIfNeeded()
            cache[pubkey] = Entry(user, Clock.System.now().toEpochMilliseconds())
        }
    }

    suspend fun putAll(users: Map<String, NostrUser>) {
        if (!config.cacheUserProfile || users.isEmpty()) return
        val now = Clock.System.now().toEpochMilliseconds()
        mutex.withLock {
            evictIfNeeded()
            for ((pk, user) in users) {
                cache[pk] = Entry(user, now)
            }
        }
    }

    private fun evictIfNeeded() {
        if (cache.size >= MAX_ENTRIES) {
            val iterator = cache.iterator()
            repeat(MAX_ENTRIES / 10) {
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
    }
}
