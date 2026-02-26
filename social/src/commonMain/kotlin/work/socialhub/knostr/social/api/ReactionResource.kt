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

    @JsExport.Ignore
    fun likeBlocking(eventId: String, authorPubkey: String): Response<NostrEvent>

    @JsExport.Ignore
    fun reactBlocking(eventId: String, authorPubkey: String, content: String): Response<NostrEvent>

    @JsExport.Ignore
    fun getReactionsBlocking(eventId: String): Response<List<NostrReaction>>
}
