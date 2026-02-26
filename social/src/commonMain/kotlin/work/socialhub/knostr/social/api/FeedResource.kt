package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.social.model.NostrNote
import kotlin.js.JsExport

@JsExport
interface FeedResource {

    /** Get home feed (posts from followed users) */
    suspend fun getHomeFeed(since: Long? = null, until: Long? = null, limit: Int = 50): Response<List<NostrNote>>

    /** Post a new text note (kind:1) */
    suspend fun post(content: String, tags: List<List<String>> = listOf()): Response<NostrEvent>

    /** Reply to a note (NIP-10 threading) */
    suspend fun reply(content: String, replyToEventId: String, rootEventId: String? = null): Response<NostrEvent>

    /** Repost a note (kind:6) */
    suspend fun repost(eventId: String): Response<NostrEvent>

    /** Delete a note (kind:5) */
    suspend fun delete(eventId: String, reason: String = ""): Response<Boolean>

    @JsExport.Ignore
    fun getHomeFeedBlocking(since: Long? = null, until: Long? = null, limit: Int = 50): Response<List<NostrNote>>

    @JsExport.Ignore
    fun postBlocking(content: String, tags: List<List<String>> = listOf()): Response<NostrEvent>

    @JsExport.Ignore
    fun replyBlocking(content: String, replyToEventId: String, rootEventId: String? = null): Response<NostrEvent>

    @JsExport.Ignore
    fun repostBlocking(eventId: String): Response<NostrEvent>

    @JsExport.Ignore
    fun deleteBlocking(eventId: String, reason: String = ""): Response<Boolean>
}
