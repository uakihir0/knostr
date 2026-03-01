package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.social.model.NostrList
import kotlin.js.JsExport

@JsExport
interface ListResource {

    /** Create a new people list (NIP-51, kind:30000) */
    suspend fun createList(name: String, pubkeys: List<String> = listOf()): Response<NostrEvent>

    /** Add a pubkey to a list */
    suspend fun addToList(name: String, pubkey: String): Response<NostrEvent>

    /** Remove a pubkey from a list */
    suspend fun removeFromList(name: String, pubkey: String): Response<NostrEvent>

    /** Get a specific list by name */
    suspend fun getList(name: String): Response<NostrList>

    /** Get all lists for the authenticated user */
    suspend fun getLists(): Response<List<NostrList>>

    /** Get all lists for a specific user */
    suspend fun getLists(pubkey: String): Response<List<NostrList>>

    @JsExport.Ignore
    fun createListBlocking(name: String, pubkeys: List<String> = listOf()): Response<NostrEvent>

    @JsExport.Ignore
    fun addToListBlocking(name: String, pubkey: String): Response<NostrEvent>

    @JsExport.Ignore
    fun removeFromListBlocking(name: String, pubkey: String): Response<NostrEvent>

    @JsExport.Ignore
    fun getListBlocking(name: String): Response<NostrList>

    @JsExport.Ignore
    fun getListsBlocking(): Response<List<NostrList>>

    @JsExport.Ignore
    fun getListsBlocking(pubkey: String): Response<List<NostrList>>
}
