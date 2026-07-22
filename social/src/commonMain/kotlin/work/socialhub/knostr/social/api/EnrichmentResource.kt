package work.socialhub.knostr.social.api

import work.socialhub.knostr.social.model.SocialDataBatch
import work.socialhub.knostr.social.model.SocialDataRequest
import kotlin.js.JsExport

@JsExport
interface EnrichmentResource {
    /** Called on a background coroutine with each successfully resolved batch. */
    var onUpdateCallback: ((SocialDataBatch) -> Unit)?

    /** Queue data for asynchronous resolution. */
    fun request(request: SocialDataRequest, forceRefresh: Boolean = true)

    /** Cancel queued work while keeping this resource reusable. */
    suspend fun cancelPending()

    /** Cancel queued work while keeping this resource reusable. */
    @JsExport.Ignore
    fun cancelPendingBlocking()

    /** Permanently stop background enrichment and release its callback. */
    fun close()
}
