package work.socialhub.knostr.social.stream

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.social.internal.SocialMapper
import work.socialhub.knostr.social.model.NostrNote
import work.socialhub.knostr.social.model.NostrUser
import kotlin.time.Clock

/**
 * Real-time timeline stream.
 * Subscribes to kind:1 events from followed users.
 */
class TimelineStream(
    private val nostr: Nostr,
) {
    var onNoteCallback: ((NostrNote) -> Unit)? = null
    var onErrorCallback: ((Exception) -> Unit)? = null

    private var subscriptionId: String? = null
    private val authorCache = mutableMapOf<String, NostrUser>()
    private val cacheMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Start streaming home timeline for the given list of followed pubkeys */
    suspend fun start(followingPubkeys: List<String>) {
        if (followingPubkeys.isEmpty()) return

        // Capture since before any async work to avoid missing notes during prefetch
        val since = Clock.System.now().epochSeconds

        val filter = NostrFilter(
            authors = followingPubkeys,
            kinds = listOf(EventKind.TEXT_NOTE),
            since = since,
        )

        subscriptionId = nostr.relayPool().subscribe(
            filters = listOf(filter),
            onEvent = { event ->
                scope.launch {
                    try {
                        val note = SocialMapper.toNote(event)
                        if (note.author == null) {
                            cacheMutex.withLock {
                                note.author = authorCache[event.pubkey]
                            }
                        }
                        onNoteCallback?.invoke(note)
                    } catch (e: Exception) {
                        onErrorCallback?.invoke(e)
                    }
                }
            },
        )

        // Prefetch profiles in the background after subscription is active
        scope.launch {
            prefetchProfiles(followingPubkeys)
        }
    }

    private suspend fun prefetchProfiles(pubkeys: List<String>) {
        for (batch in pubkeys.chunked(50)) {
            try {
                val filter = NostrFilter(
                    authors = batch,
                    kinds = listOf(EventKind.METADATA),
                )
                val response = nostr.events().queryEvents(listOf(filter))
                val users = response.data
                    .sortedByDescending { it.createdAt }
                    .distinctBy { it.pubkey }
                cacheMutex.withLock {
                    users.forEach { event ->
                        authorCache[event.pubkey] = SocialMapper.toUser(event)
                    }
                }
            } catch (_: Exception) {
                // Best-effort per batch: continue with remaining batches
            }
        }
    }

    /** Stop streaming */
    suspend fun stop() {
        subscriptionId?.let {
            nostr.relayPool().unsubscribe(it)
            subscriptionId = null
        }
        scope.cancel()
    }
}
