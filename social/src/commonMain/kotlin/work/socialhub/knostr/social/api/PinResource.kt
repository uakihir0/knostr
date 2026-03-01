package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import kotlin.js.JsExport

@JsExport
interface PinResource {

    /** Pin a note (NIP-51 kind:10001) */
    suspend fun pin(eventId: String): Response<NostrEvent>

    /** Unpin a note */
    suspend fun unpin(eventId: String): Response<NostrEvent>

    /** Get list of pinned event IDs */
    suspend fun getPinList(): Response<List<String>>

    @JsExport.Ignore
    fun pinBlocking(eventId: String): Response<NostrEvent>

    @JsExport.Ignore
    fun unpinBlocking(eventId: String): Response<NostrEvent>

    @JsExport.Ignore
    fun getPinListBlocking(): Response<List<String>>
}
