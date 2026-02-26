package work.socialhub.knostr.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import kotlin.js.JsExport

@JsExport
interface EventResource {

    /** Publish a signed event to all connected relays */
    suspend fun publishEvent(event: NostrEvent): Response<Boolean>

    /** Query events matching the given filters */
    suspend fun queryEvents(filters: List<NostrFilter>): Response<List<NostrEvent>>

    /** Delete an event by publishing a kind:5 deletion event */
    suspend fun deleteEvent(eventId: String, reason: String = ""): Response<Boolean>

    @JsExport.Ignore
    fun publishEventBlocking(event: NostrEvent): Response<Boolean>

    @JsExport.Ignore
    fun queryEventsBlocking(filters: List<NostrFilter>): Response<List<NostrEvent>>

    @JsExport.Ignore
    fun deleteEventBlocking(eventId: String, reason: String = ""): Response<Boolean>
}
