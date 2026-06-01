package work.socialhub.knostr.social.stream

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

    /** Start streaming home timeline for the given list of followed pubkeys */
    suspend fun start(followingPubkeys: List<String>) {
        if (followingPubkeys.isEmpty()) return

        // Pre-fetch profiles for all followed users so streaming notes have author info
        prefetchProfiles(followingPubkeys)

        val filter = NostrFilter(
            authors = followingPubkeys,
            kinds = listOf(EventKind.TEXT_NOTE),
            since = Clock.System.now().epochSeconds,
        )

        subscriptionId = nostr.relayPool().subscribe(
            filters = listOf(filter),
            onEvent = { event ->
                try {
                    val note = SocialMapper.toNote(event)
                    if (note.author == null) {
                        note.author = authorCache[event.pubkey]
                    }
                    onNoteCallback?.invoke(note)
                } catch (e: Exception) {
                    onErrorCallback?.invoke(e)
                }
            },
        )
    }

    private suspend fun prefetchProfiles(pubkeys: List<String>) {
        try {
            for (batch in pubkeys.chunked(50)) {
                val filter = NostrFilter(
                    authors = batch,
                    kinds = listOf(EventKind.METADATA),
                )
                val response = nostr.events().queryEvents(listOf(filter))
                response.data
                    .sortedByDescending { it.createdAt }
                    .distinctBy { it.pubkey }
                    .forEach { event ->
                        authorCache[event.pubkey] = SocialMapper.toUser(event)
                    }
            }
        } catch (_: Exception) {
            // Best-effort: streaming will still work, just without author info for uncached users
        }
    }

    /** Stop streaming */
    suspend fun stop() {
        subscriptionId?.let {
            nostr.relayPool().unsubscribe(it)
            subscriptionId = null
        }
    }
}
