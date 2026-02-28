package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.social.model.NostrZap
import kotlin.js.JsExport

@JsExport
interface ZapResource {

    /**
     * Create a zap request event (kind:9734) for a user.
     * The caller is responsible for sending this to the recipient's LNURL pay endpoint.
     * @param recipientPubkey The pubkey of the user to zap
     * @param amountMilliSats Amount in millisatoshis
     * @param relays List of relays to include in the zap request
     * @param message Optional zap message
     * @param eventId Optional event ID to zap (null for profile zap)
     */
    suspend fun createZapRequest(
        recipientPubkey: String,
        amountMilliSats: Long,
        relays: List<String>,
        message: String = "",
        eventId: String? = null,
    ): Response<NostrEvent>

    /**
     * Get zap receipts (kind:9735) for a specific event.
     * @param eventId The event ID to get zaps for
     * @param limit Maximum number of zaps to return
     */
    suspend fun getZapsForEvent(eventId: String, limit: Int = 50): Response<List<NostrZap>>

    /**
     * Get zap receipts (kind:9735) for a specific user.
     * @param pubkey The pubkey to get zaps for
     * @param limit Maximum number of zaps to return
     */
    suspend fun getZapsForUser(pubkey: String, limit: Int = 50): Response<List<NostrZap>>

    /**
     * Get the LNURL pay info for a user (from their profile's lud16 field).
     * @param lud16 The Lightning address (e.g., user@getalby.com)
     * @return The LNURL pay callback URL
     */
    suspend fun getLnurlPayInfo(lud16: String): Response<LnurlPayInfo>

    @JsExport.Ignore
    fun createZapRequestBlocking(
        recipientPubkey: String,
        amountMilliSats: Long,
        relays: List<String>,
        message: String = "",
        eventId: String? = null,
    ): Response<NostrEvent>

    @JsExport.Ignore
    fun getZapsForEventBlocking(eventId: String, limit: Int = 50): Response<List<NostrZap>>

    @JsExport.Ignore
    fun getZapsForUserBlocking(pubkey: String, limit: Int = 50): Response<List<NostrZap>>

    @JsExport.Ignore
    fun getLnurlPayInfoBlocking(lud16: String): Response<LnurlPayInfo>
}
