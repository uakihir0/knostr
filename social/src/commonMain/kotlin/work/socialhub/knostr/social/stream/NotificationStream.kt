package work.socialhub.knostr.social.stream

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import work.socialhub.knostr.social.model.NostrReaction
import work.socialhub.knostr.social.model.NostrUser
import work.socialhub.knostr.social.model.SocialDataBatch
import work.socialhub.knostr.social.model.SocialDataRequest
import kotlin.time.Clock

/**
 * Real-time notification stream.
 * Subscribes to mentions (kind:1), reactions (kind:7), and reposts (kind:6)
 * targeting the user's pubkey.
 */
class NotificationStream(
    private val nostr: Nostr,
    private val socialCache: SocialCache,
    private val enrichment: EnrichmentResource? = null,
) {
    var onMentionCallback: ((NostrNote) -> Unit)? = null
    var onReactionCallback: ((NostrReaction) -> Unit)? = null
    var onRepostCallback: ((NostrEvent) -> Unit)? = null
    var onErrorCallback: ((Exception) -> Unit)? = null

    private var subscriptionId: String? = null
    private var scope: CoroutineScope? = null
    private var eventChannel: Channel<NostrEvent>? = null

    /** Start streaming notifications for the given pubkey */
    suspend fun start(myPubkey: String) {
        val newScope = CoroutineScope(SupervisorJob())
        scope = newScope

        val channel = Channel<NostrEvent>(Channel.UNLIMITED)
        eventChannel = channel

        newScope.launch {
            for (event in channel) {
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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onErrorCallback?.invoke(e)
                }
            }
        }

        val filter = NostrFilter(
            kinds = listOf(EventKind.TEXT_NOTE, EventKind.REACTION, EventKind.REPOST),
            pTags = listOf(myPubkey),
            since = Clock.System.now().epochSeconds,
        )

        subscriptionId = nostr.relayPool().subscribe(
            filters = listOf(filter),
            onEvent = { event ->
                channel.trySend(event)
            },
        )
    }

    private suspend fun resolveAuthor(pubkey: String): NostrUser? {
        try {
            socialCache.get(SocialDataRequest(userPubkeys = listOf(pubkey)))
                .users.firstOrNull { it.pubkey == pubkey }
                ?.let { return it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Fall through to relay lookup.
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
                try {
                    socialCache.put(SocialDataBatch(users = listOf(user)))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A cache failure does not discard relay data.
                }
                return user
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort
        }
        enrichment?.request(
            SocialDataRequest(userPubkeys = listOf(pubkey)),
            forceRefresh = false,
        )
        return null
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
