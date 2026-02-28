package work.socialhub.knostr.social.stream

import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.social.internal.SocialMapper
import work.socialhub.knostr.social.model.NostrNote
import work.socialhub.knostr.social.model.NostrReaction
import work.socialhub.knostr.entity.NostrEvent
import kotlin.time.Clock

/**
 * Real-time notification stream.
 * Subscribes to mentions (kind:1), reactions (kind:7), and reposts (kind:6)
 * targeting the user's pubkey.
 */
class NotificationStream(
    private val nostr: Nostr,
) {
    var onMentionCallback: ((NostrNote) -> Unit)? = null
    var onReactionCallback: ((NostrReaction) -> Unit)? = null
    var onRepostCallback: ((NostrEvent) -> Unit)? = null
    var onErrorCallback: ((Exception) -> Unit)? = null

    private var subscriptionId: String? = null

    /** Start streaming notifications for the given pubkey */
    suspend fun start(myPubkey: String) {
        val filter = NostrFilter(
            kinds = listOf(EventKind.TEXT_NOTE, EventKind.REACTION, EventKind.REPOST),
            pTags = listOf(myPubkey),
            since = Clock.System.now().epochSeconds,
        )

        subscriptionId = nostr.relayPool().subscribe(
            filters = listOf(filter),
            onEvent = { event ->
                try {
                    when (event.kind) {
                        EventKind.TEXT_NOTE -> {
                            val note = SocialMapper.toNote(event)
                            onMentionCallback?.invoke(note)
                        }
                        EventKind.REACTION -> {
                            val reaction = SocialMapper.toReaction(event)
                            onReactionCallback?.invoke(reaction)
                        }
                        EventKind.REPOST -> {
                            onRepostCallback?.invoke(event)
                        }
                    }
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
