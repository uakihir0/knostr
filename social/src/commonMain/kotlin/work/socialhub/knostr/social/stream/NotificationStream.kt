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
import work.socialhub.knostr.social.model.NostrReaction
import work.socialhub.knostr.social.model.NostrUser
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
    private val authorCache = mutableMapOf<String, NostrUser>()
    private val cacheMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
                scope.launch {
                    try {
                        when (event.kind) {
                            EventKind.TEXT_NOTE -> {
                                val note = SocialMapper.toNote(event)
                                if (note.author == null) {
                                    note.author = resolveAuthor(event.pubkey)
                                }
                                onMentionCallback?.invoke(note)
                            }
                            EventKind.REACTION -> {
                                val reaction = SocialMapper.toReaction(event)
                                if (reaction.author == null) {
                                    reaction.author = resolveAuthor(event.pubkey)
                                }
                                onReactionCallback?.invoke(reaction)
                            }
                            EventKind.REPOST -> {
                                onRepostCallback?.invoke(event)
                            }
                        }
                    } catch (e: Exception) {
                        onErrorCallback?.invoke(e)
                    }
                }
            },
        )
    }

    private suspend fun resolveAuthor(pubkey: String): NostrUser? {
        cacheMutex.withLock {
            authorCache[pubkey]?.let { return it }
        }
        try {
            val filter = NostrFilter(
                authors = listOf(pubkey),
                kinds = listOf(EventKind.METADATA),
                limit = 1,
            )
            val response = nostr.events().queryEvents(listOf(filter))
            val event = response.data
                .sortedByDescending { it.createdAt }
                .firstOrNull()
            if (event != null) {
                val user = SocialMapper.toUser(event)
                cacheMutex.withLock {
                    authorCache[pubkey] = user
                }
                return user
            }
        } catch (_: Exception) {
            // Best-effort
        }
        return null
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
