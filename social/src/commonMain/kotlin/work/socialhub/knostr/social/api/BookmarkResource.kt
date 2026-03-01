package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import kotlin.js.JsExport

@JsExport
interface BookmarkResource {

    /** Bookmark a note (NIP-51 kind:10003) */
    suspend fun bookmark(eventId: String): Response<NostrEvent>

    /** Remove a note from bookmarks */
    suspend fun unbookmark(eventId: String): Response<NostrEvent>

    /** Get list of bookmarked event IDs */
    suspend fun getBookmarks(): Response<List<String>>

    @JsExport.Ignore
    fun bookmarkBlocking(eventId: String): Response<NostrEvent>

    @JsExport.Ignore
    fun unbookmarkBlocking(eventId: String): Response<NostrEvent>

    @JsExport.Ignore
    fun getBookmarksBlocking(): Response<List<String>>
}
