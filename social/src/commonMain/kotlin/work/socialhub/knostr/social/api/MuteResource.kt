package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import kotlin.js.JsExport

@JsExport
interface MuteResource {

    /** Add a user to the public mute list (NIP-51 kind:10000) */
    suspend fun mute(pubkey: String): Response<NostrEvent>

    /** Remove a user from the public mute list */
    suspend fun unmute(pubkey: String): Response<NostrEvent>

    /** Get the list of muted pubkeys */
    suspend fun getMuteList(): Response<List<String>>

    @JsExport.Ignore
    fun muteBlocking(pubkey: String): Response<NostrEvent>

    @JsExport.Ignore
    fun unmuteBlocking(pubkey: String): Response<NostrEvent>

    @JsExport.Ignore
    fun getMuteListBlocking(): Response<List<String>>
}
