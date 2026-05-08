package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.social.model.NostrNote
import work.socialhub.knostr.social.model.NostrThread
import kotlin.js.JsExport

@JsExport
interface FeedResource {

    /** Get home feed (posts from followed users) */
    suspend fun getHomeFeed(since: Long? = null, until: Long? = null, limit: Int = 50, excludeSensitive: Boolean = false): Response<List<NostrNote>>

    /** Get a single note by event ID */
    suspend fun getNote(eventId: String): Response<NostrNote>

    /** Get notes posted by a specific user */
    suspend fun getUserFeed(pubkey: String, since: Long? = null, until: Long? = null, limit: Int = 50, excludeSensitive: Boolean = false): Response<List<NostrNote>>

    /** Get notes mentioning the authenticated user (p-tag) */
    suspend fun getMentions(since: Long? = null, until: Long? = null, limit: Int = 50, excludeSensitive: Boolean = false): Response<List<NostrNote>>

    /** Get thread context (ancestors + descendants) for a note */
    suspend fun getThread(eventId: String): Response<NostrThread>

    /** Post a new text note (kind:1) */
    suspend fun post(
        content: String,
        tags: List<List<String>> = listOf(),
        contentWarning: String? = null,
        expiry: Long? = null,
        sensitive: Boolean = false,
    ): Response<NostrEvent>

    /** Reply to a note (NIP-10 threading) */
    suspend fun reply(
        content: String,
        replyToEventId: String,
        rootEventId: String? = null,
        contentWarning: String? = null,
        expiry: Long? = null,
        sensitive: Boolean = false,
    ): Response<NostrEvent>

    /** Repost a note (kind:6) */
    suspend fun repost(eventId: String): Response<NostrEvent>

    /** Quote repost a note (kind:1 with q tag, NIP-18) */
    suspend fun quoteRepost(
        eventId: String,
        comment: String,
        contentWarning: String? = null,
        expiry: Long? = null,
        sensitive: Boolean = false,
    ): Response<NostrEvent>

    /** Delete a note (kind:5) */
    suspend fun delete(eventId: String, reason: String = ""): Response<Boolean>

    /** Get posts that the user has liked (via kind:7 reaction lookup) */
    suspend fun getUserLikesFeed(pubkey: String, since: Long? = null, until: Long? = null, limit: Int = 50, excludeSensitive: Boolean = false): Response<List<NostrNote>>

    /** Get posts with media attachments (via imeta tag filter) */
    suspend fun getUserMediaFeed(pubkey: String, since: Long? = null, until: Long? = null, limit: Int = 50, excludeSensitive: Boolean = false): Response<List<NostrNote>>

    /** Resolve note1... bech32 to event */
    suspend fun getNoteByNpub(noteId: String): Response<NostrNote>

    @JsExport.Ignore
    fun getHomeFeedBlocking(since: Long? = null, until: Long? = null, limit: Int = 50, excludeSensitive: Boolean = false): Response<List<NostrNote>>

    @JsExport.Ignore
    fun getNoteBlocking(eventId: String): Response<NostrNote>

    @JsExport.Ignore
    fun getUserFeedBlocking(pubkey: String, since: Long? = null, until: Long? = null, limit: Int = 50, excludeSensitive: Boolean = false): Response<List<NostrNote>>

    @JsExport.Ignore
    fun getMentionsBlocking(since: Long? = null, until: Long? = null, limit: Int = 50, excludeSensitive: Boolean = false): Response<List<NostrNote>>

    @JsExport.Ignore
    fun getThreadBlocking(eventId: String): Response<NostrThread>

    @JsExport.Ignore
    fun postBlocking(
        content: String,
        tags: List<List<String>> = listOf(),
        contentWarning: String? = null,
        expiry: Long? = null,
        sensitive: Boolean = false,
    ): Response<NostrEvent>

    @JsExport.Ignore
    fun replyBlocking(
        content: String,
        replyToEventId: String,
        rootEventId: String? = null,
        contentWarning: String? = null,
        expiry: Long? = null,
        sensitive: Boolean = false,
    ): Response<NostrEvent>

    @JsExport.Ignore
    fun repostBlocking(eventId: String): Response<NostrEvent>

    @JsExport.Ignore
    fun quoteRepostBlocking(
        eventId: String,
        comment: String,
        contentWarning: String? = null,
        expiry: Long? = null,
        sensitive: Boolean = false,
    ): Response<NostrEvent>

    @JsExport.Ignore
    fun deleteBlocking(eventId: String, reason: String = ""): Response<Boolean>

    @JsExport.Ignore
    fun getUserLikesFeedBlocking(pubkey: String, since: Long? = null, until: Long? = null, limit: Int = 50, excludeSensitive: Boolean = false): Response<List<NostrNote>>

    @JsExport.Ignore
    fun getUserMediaFeedBlocking(pubkey: String, since: Long? = null, until: Long? = null, limit: Int = 50, excludeSensitive: Boolean = false): Response<List<NostrNote>>

    @JsExport.Ignore
    fun getNoteByNpubBlocking(noteId: String): Response<NostrNote>
}
