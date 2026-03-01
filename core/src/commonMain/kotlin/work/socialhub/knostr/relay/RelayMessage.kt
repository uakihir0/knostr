package work.socialhub.knostr.relay

import work.socialhub.knostr.entity.NostrEvent

/**
 * Parsed relay message types (NIP-01).
 */
sealed class RelayMessage {
    /** Relay sends an event matching a subscription */
    data class EventMsg(val subscriptionId: String, val event: NostrEvent) : RelayMessage()

    /** Relay acknowledges an EVENT message */
    data class OkMsg(val eventId: String, val success: Boolean, val message: String) : RelayMessage()

    /** Relay signals end of stored events for a subscription */
    data class EoseMsg(val subscriptionId: String) : RelayMessage()

    /** Relay closed a subscription */
    data class ClosedMsg(val subscriptionId: String, val message: String) : RelayMessage()

    /** Relay sends a human-readable notice */
    data class NoticeMsg(val message: String) : RelayMessage()

    /** Relay requests authentication (NIP-42) */
    data class AuthMsg(val challenge: String) : RelayMessage()
}
