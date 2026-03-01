package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.social.model.NostrRelayListEntry
import kotlin.js.JsExport

@JsExport
interface RelayListResource {

    /** Get relay list for the authenticated user (NIP-65 kind:10002) */
    suspend fun getRelayList(): Response<List<NostrRelayListEntry>>

    /** Get relay list for a specific user */
    suspend fun getRelayList(pubkey: String): Response<List<NostrRelayListEntry>>

    /** Set relay list (replaces existing, kind:10002) */
    suspend fun setRelayList(relays: List<NostrRelayListEntry>): Response<NostrEvent>

    @JsExport.Ignore
    fun getRelayListBlocking(): Response<List<NostrRelayListEntry>>

    @JsExport.Ignore
    fun getRelayListBlocking(pubkey: String): Response<List<NostrRelayListEntry>>

    @JsExport.Ignore
    fun setRelayListBlocking(relays: List<NostrRelayListEntry>): Response<NostrEvent>
}
