package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.social.model.NostrReaction
import kotlin.js.JsExport

@JsExport
interface ReactionResource {

    /** Like a note (kind:7, content: "+") */
    suspend fun like(eventId: String, authorPubkey: String): Response<NostrEvent>

    /** React with custom content (kind:7) */
    suspend fun react(eventId: String, authorPubkey: String, content: String): Response<NostrEvent>

    /** Get reactions for a note */
    suspend fun getReactions(eventId: String): Response<List<NostrReaction>>

    /** Remove a like (find own kind:7 "+" for eventId, then delete via kind:5) */
    suspend fun unlike(eventId: String): Response<Boolean>

    /** Remove a reaction (find own kind:7 with matching content, then delete) */
    suspend fun unreact(eventId: String, content: String? = null): Response<Boolean>

    /** Get reactions made by a specific user */
    suspend fun getUserReactions(pubkey: String, since: Long? = null, until: Long? = null, limit: Int = 50): Response<List<NostrReaction>>

    @JsExport.Ignore
    fun likeBlocking(eventId: String, authorPubkey: String): Response<NostrEvent>

    @JsExport.Ignore
    fun reactBlocking(eventId: String, authorPubkey: String, content: String): Response<NostrEvent>

    @JsExport.Ignore
    fun getReactionsBlocking(eventId: String): Response<List<NostrReaction>>

    @JsExport.Ignore
    fun unlikeBlocking(eventId: String): Response<Boolean>

    @JsExport.Ignore
    fun unreactBlocking(eventId: String, content: String? = null): Response<Boolean>

    @JsExport.Ignore
    fun getUserReactionsBlocking(pubkey: String, since: Long? = null, until: Long? = null, limit: Int = 50): Response<List<NostrReaction>>
}
