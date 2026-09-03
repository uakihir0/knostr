package work.socialhub.knostr.api

import kotlinx.coroutines.withTimeoutOrNull
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.util.toBlocking
import kotlin.js.JsExport

@JsExport
interface EventResource {

    /** Publish a signed event to all connected relays */
    suspend fun publishEvent(event: NostrEvent): Response<Boolean>

    /** Query events matching the given filters */
    suspend fun queryEvents(filters: List<NostrFilter>): Response<List<NostrEvent>>

    /**
     * Query events, waiting at most [timeoutMs] for EOSE instead of the
     * configured query timeout. [Response.isComplete] is false when the wait
     * ran out first.
     *
     * An implementation that receives events one by one should override this to
     * report the events it collected before the wait ran out, the way
     * [work.socialhub.knostr.internal.EventResourceImpl] does. This fallback
     * cannot see them: it cancels [queryEvents] and reports an empty incomplete
     * response.
     */
    suspend fun queryEventsWithTimeout(
        filters: List<NostrFilter>,
        timeoutMs: Long,
    ): Response<List<NostrEvent>> {
        return withTimeoutOrNull(timeoutMs) { queryEvents(filters) }
            ?: Response<List<NostrEvent>>(listOf()).also { it.isComplete = false }
    }

    /** Delete an event by publishing a kind:5 deletion event */
    suspend fun deleteEvent(eventId: String, reason: String = ""): Response<Boolean>

    @JsExport.Ignore
    fun publishEventBlocking(event: NostrEvent): Response<Boolean>

    @JsExport.Ignore
    fun queryEventsBlocking(filters: List<NostrFilter>): Response<List<NostrEvent>>

    @JsExport.Ignore
    fun queryEventsWithTimeoutBlocking(
        filters: List<NostrFilter>,
        timeoutMs: Long,
    ): Response<List<NostrEvent>> {
        return toBlocking { queryEventsWithTimeout(filters, timeoutMs) }
    }

    @JsExport.Ignore
    fun deleteEventBlocking(eventId: String, reason: String = ""): Response<Boolean>
}
