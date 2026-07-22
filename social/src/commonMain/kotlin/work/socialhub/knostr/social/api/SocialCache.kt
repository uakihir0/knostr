package work.socialhub.knostr.social.api

import work.socialhub.knostr.social.model.SocialDataBatch
import work.socialhub.knostr.social.model.SocialDataRequest
import work.socialhub.knostr.util.toBlocking
import kotlin.js.JsExport

/**
 * Read-through/write-through cache for social enrichment data.
 *
 * Implementations own their freshness policy and should omit stale values from [get].
 * Methods may be called concurrently.
 */
@JsExport
interface SocialCache {
    suspend fun get(request: SocialDataRequest): SocialDataBatch
    suspend fun put(batch: SocialDataBatch)

    /** Remove all entries identified by [request]. */
    suspend fun remove(request: SocialDataRequest)

    @JsExport.Ignore
    fun getBlocking(request: SocialDataRequest): SocialDataBatch = toBlocking { get(request) }

    @JsExport.Ignore
    fun putBlocking(batch: SocialDataBatch) = toBlocking { put(batch) }

    @JsExport.Ignore
    fun removeBlocking(request: SocialDataRequest) = toBlocking { remove(request) }
}
