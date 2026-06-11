package work.socialhub.knostr.social.stream

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.social.internal.ProfileCache
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
    private val profileCache: ProfileCache? = null,
) {
    var onNoteCallback: ((NostrNote) -> Unit)? = null
    var onErrorCallback: ((Exception) -> Unit)? = null

    private var subscriptionId: String? = null
    private val localCache = mutableMapOf<String, NostrUser>()
    private val cacheMutex = Mutex()
    private var scope: CoroutineScope? = null
    private var eventChannel: Channel<NostrEvent>? = null

    /** Start streaming home timeline for the given list of followed pubkeys */
    suspend fun start(followingPubkeys: List<String>) {
        if (followingPubkeys.isEmpty()) return

        val newScope = CoroutineScope(SupervisorJob())
        scope = newScope

        // Capture since before prefetch to avoid missing notes
        val since = Clock.System.now().epochSeconds

        // Prefetch profiles so initial notes already have author info
        prefetchProfiles(followingPubkeys)

        // Channel serializes event processing to preserve relay ordering
        val channel = Channel<NostrEvent>(Channel.UNLIMITED)
        eventChannel = channel

        newScope.launch {
            for (event in channel) {
                try {
                    val note = SocialMapper.toNote(event)
                    if (note.author == null) {
                        note.author = profileCache?.get(event.pubkey)
                            ?: cacheMutex.withLock { localCache[event.pubkey] }
                    }
                    onNoteCallback?.invoke(note)
                } catch (e: Exception) {
                    onErrorCallback?.invoke(e)
                }
            }
        }

        val filter = NostrFilter(
            authors = followingPubkeys,
            kinds = listOf(EventKind.TEXT_NOTE),
            since = since,
        )

        subscriptionId = nostr.relayPool().subscribe(
            filters = listOf(filter),
            onEvent = { event ->
                channel.trySend(event)
            },
        )
    }

    private suspend fun prefetchProfiles(pubkeys: List<String>) {
        for (batch in pubkeys.chunked(100)) {
            try {
                val filter = NostrFilter(
                    authors = batch,
                    kinds = listOf(EventKind.METADATA),
                )
                val response = nostr.events().queryEvents(listOf(filter))
                val users = response.data
                    .sortedByDescending { it.createdAt }
                    .distinctBy { it.pubkey }
                val mapped = users.associate { it.pubkey to SocialMapper.toUser(it) }
                profileCache?.putAll(mapped)
                cacheMutex.withLock {
                    localCache.putAll(mapped)
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
        eventChannel?.close()
        eventChannel = null
        scope?.cancel()
        scope = null
    }
}
