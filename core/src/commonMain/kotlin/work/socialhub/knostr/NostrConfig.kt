package work.socialhub.knostr

import work.socialhub.knostr.signing.NostrSigner
import kotlin.js.JsExport

@JsExport
class NostrConfig {
    /** Relay WebSocket URLs (e.g., wss://relay.damus.io) */
    var relayUrls: List<String> = listOf()

    /** Signer for event signing (null for read-only) */
    var signer: NostrSigner? = null

    /** WebSocket connect timeout in milliseconds */
    var connectTimeoutMs: Long = 10_000

    /** Query timeout in milliseconds (waiting for EOSE) */
    var queryTimeoutMs: Long = 30_000

    /** Whether to automatically reconnect on disconnect */
    var autoReconnect: Boolean = true

    /** Maximum number of reconnection attempts */
    var maxReconnectAttempts: Int = 5

    /** Initial reconnection delay in milliseconds (exponential backoff) */
    var reconnectDelayMs: Long = 1_000

    /** Whether to automatically respond to relay AUTH challenges (NIP-42) */
    var autoAuth: Boolean = true
}
