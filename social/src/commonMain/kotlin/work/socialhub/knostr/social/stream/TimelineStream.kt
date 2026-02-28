package work.socialhub.knostr.social.stream

import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.social.internal.SocialMapper
import work.socialhub.knostr.social.model.NostrNote
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

    /** Start streaming home timeline for the given list of followed pubkeys */
    suspend fun start(followingPubkeys: List<String>) {
        if (followingPubkeys.isEmpty()) return

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
                    onNoteCallback?.invoke(note)
                } catch (e: Exception) {
                    onErrorCallback?.invoke(e)
                }
            },
        )
    }

    /** Stop streaming */
    suspend fun stop() {
        subscriptionId?.let {
            nostr.relayPool().unsubscribe(it)
            subscriptionId = null
        }
    }
}
