package work.socialhub.knostr.relay

import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter

/**
 * Represents an active subscription to relay events.
 */
data class Subscription(
    val id: String,
    val filters: List<NostrFilter>,
    val onEvent: (NostrEvent) -> Unit,
    /** Invoked when a relay signals end-of-stored-events, with the relay URL. */
    val onEose: ((relayUrl: String) -> Unit)? = null,
)
