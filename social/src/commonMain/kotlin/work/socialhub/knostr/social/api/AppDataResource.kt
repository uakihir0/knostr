package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import kotlin.js.JsExport

@JsExport
interface AppDataResource {

    /** Set app-specific data (NIP-78, kind:30078). Content is stored as-is. */
    suspend fun setAppData(dTag: String, content: String): Response<NostrEvent>

    /** Get own app-specific data by d-tag */
    suspend fun getAppData(dTag: String): Response<String?>

    /** Get another user's app-specific data by pubkey and d-tag */
    suspend fun getAppDataByPubkey(pubkey: String, dTag: String): Response<String?>

    @JsExport.Ignore
    fun setAppDataBlocking(dTag: String, content: String): Response<NostrEvent>

    @JsExport.Ignore
    fun getAppDataBlocking(dTag: String): Response<String?>

    @JsExport.Ignore
    fun getAppDataByPubkeyBlocking(pubkey: String, dTag: String): Response<String?>
}
