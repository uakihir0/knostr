package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.social.model.NostrNote
import work.socialhub.knostr.social.model.NostrUser
import kotlin.js.JsExport

@JsExport
interface SearchResource {

    /** Search for notes (NIP-50) */
    suspend fun searchNotes(query: String, limit: Int = 50): Response<List<NostrNote>>

    /** Search for users (NIP-50, kind:0) */
    suspend fun searchUsers(query: String, limit: Int = 50): Response<List<NostrUser>>

    @JsExport.Ignore
    fun searchNotesBlocking(query: String, limit: Int = 50): Response<List<NostrNote>>

    @JsExport.Ignore
    fun searchUsersBlocking(query: String, limit: Int = 50): Response<List<NostrUser>>
}
