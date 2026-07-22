package work.socialhub.knostr.social.stream

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.social.api.EnrichmentResource
import work.socialhub.knostr.social.api.SocialCache
import work.socialhub.knostr.social.internal.SocialMapper
import work.socialhub.knostr.social.model.NostrNote
import work.socialhub.knostr.social.model.NostrUser
import work.socialhub.knostr.social.model.SocialDataBatch
import work.socialhub.knostr.social.model.SocialDataRequest
import kotlin.time.Clock

/**
 * Real-time timeline stream.
 * Subscribes to kind:1 events from followed users.
 */
class TimelineStream(
    private val nostr: Nostr,
    private val socialCache: SocialCache,
    private val enrichment: EnrichmentResource? = null,
) {
    var onNoteCallback: ((NostrNote) -> Unit)? = null
    var onErrorCallback: ((Exception) -> Unit)? = null

    private var subscriptionId: String? = null
    private var scope: CoroutineScope? = null
    private var processorJob: Job? = null
    private var eventChannel: Channel<NostrEvent>? = null
    private val prefetchedUsers = mutableMapOf<String, NostrUser>()

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

        processorJob = newScope.launch {
            for (event in channel) {
                try {
                    val note = SocialMapper.toNote(event)
                    if (note.author == null) {
                        note.author = cachedUser(event.pubkey)
                        if (note.author == null) {
                            enrichment?.request(
                                SocialDataRequest(userPubkeys = listOf(event.pubkey)),
                                forceRefresh = false,
                            )
                        }
                    }
                    onNoteCallback?.invoke(note)
                } catch (e: CancellationException) {
                    throw e
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
                prefetchedUsers.putAll(mapped)
                try {
                    socialCache.put(SocialDataBatch(users = mapped.values.toList()))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Keep using prefetchedUsers when an application cache is unavailable.
                }
                val missing = batch.filter { it !in mapped }
                if (missing.isNotEmpty()) {
                    enrichment?.request(
                        SocialDataRequest(userPubkeys = missing),
                        forceRefresh = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                enrichment?.request(
                    SocialDataRequest(userPubkeys = batch),
                    forceRefresh = false,
                )
            }
        }
    }

    private suspend fun cachedUser(pubkey: String): NostrUser? {
        prefetchedUsers[pubkey]?.let { return it }
        return try {
            socialCache.get(SocialDataRequest(userPubkeys = listOf(pubkey)))
                .users.firstOrNull { it.pubkey == pubkey }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
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
        processorJob?.cancelAndJoin()
        processorJob = null
        scope?.cancel()
        scope = null
        prefetchedUsers.clear()
    }
}
