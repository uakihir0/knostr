package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.social.model.NostrNote
import work.socialhub.knostr.social.model.NostrThread
import kotlin.js.JsExport

@JsExport
interface FeedResource {

    /** Get home feed (posts from followed users) */
    suspend fun getHomeFeed(since: Long? = null, until: Long? = null, limit: Int = 50): Response<List<NostrNote>>

    /** Get a single note by event ID */
    suspend fun getNote(eventId: String): Response<NostrNote>

    /** Get notes posted by a specific user */
    suspend fun getUserFeed(pubkey: String, since: Long? = null, until: Long? = null, limit: Int = 50): Response<List<NostrNote>>

    /** Get notes mentioning the authenticated user (p-tag) */
    suspend fun getMentions(since: Long? = null, until: Long? = null, limit: Int = 50): Response<List<NostrNote>>

    /** Get thread context (ancestors + descendants) for a note */
    suspend fun getThread(eventId: String): Response<NostrThread>

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
    fun getNoteBlocking(eventId: String): Response<NostrNote>

    @JsExport.Ignore
    fun getUserFeedBlocking(pubkey: String, since: Long? = null, until: Long? = null, limit: Int = 50): Response<List<NostrNote>>

    @JsExport.Ignore
    fun getMentionsBlocking(since: Long? = null, until: Long? = null, limit: Int = 50): Response<List<NostrNote>>

    @JsExport.Ignore
    fun getThreadBlocking(eventId: String): Response<NostrThread>

    @JsExport.Ignore
    fun postBlocking(content: String, tags: List<List<String>> = listOf()): Response<NostrEvent>

    @JsExport.Ignore
    fun replyBlocking(content: String, replyToEventId: String, rootEventId: String? = null): Response<NostrEvent>

    @JsExport.Ignore
    fun repostBlocking(eventId: String): Response<NostrEvent>

    @JsExport.Ignore
    fun deleteBlocking(eventId: String, reason: String = ""): Response<Boolean>
}
