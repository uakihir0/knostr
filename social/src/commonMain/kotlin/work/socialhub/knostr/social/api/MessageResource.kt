package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.social.model.NostrDirectMessage
import kotlin.js.JsExport

@JsExport
interface MessageResource {

    /** NIP-17: Send a DM using Gift Wrap pattern */
    suspend fun sendMessage(recipientPubkey: String, content: String): Response<NostrEvent>

    /** NIP-17: Get received DMs (unwrap gift wraps) */
    suspend fun getMessages(since: Long? = null, until: Long? = null, limit: Int = 50): Response<List<NostrDirectMessage>>

    /** NIP-17: Get conversation with a specific user */
    suspend fun getConversation(pubkey: String, since: Long? = null, until: Long? = null, limit: Int = 50): Response<List<NostrDirectMessage>>

    /** NIP-04 (legacy): Send an encrypted DM */
    suspend fun sendLegacyMessage(recipientPubkey: String, content: String): Response<NostrEvent>

    /** NIP-04 (legacy): Get received legacy DMs */
    suspend fun getLegacyMessages(since: Long? = null, until: Long? = null, limit: Int = 50): Response<List<NostrDirectMessage>>

    @JsExport.Ignore
    fun sendMessageBlocking(recipientPubkey: String, content: String): Response<NostrEvent>

    @JsExport.Ignore
    fun getMessagesBlocking(since: Long? = null, until: Long? = null, limit: Int = 50): Response<List<NostrDirectMessage>>

    @JsExport.Ignore
    fun getConversationBlocking(pubkey: String, since: Long? = null, until: Long? = null, limit: Int = 50): Response<List<NostrDirectMessage>>

    @JsExport.Ignore
    fun sendLegacyMessageBlocking(recipientPubkey: String, content: String): Response<NostrEvent>

    @JsExport.Ignore
    fun getLegacyMessagesBlocking(since: Long? = null, until: Long? = null, limit: Int = 50): Response<List<NostrDirectMessage>>
}
