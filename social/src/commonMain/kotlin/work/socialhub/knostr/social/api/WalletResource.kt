package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.social.model.NwcTransaction
import kotlin.js.JsExport

@JsExport
interface WalletResource {

    /** Connect to a NWC wallet service using a nostr+walletconnect:// URL */
    suspend fun connect(nwcUrl: String)

    /** Pay a Lightning invoice */
    suspend fun payInvoice(invoice: String): Response<String>

    /** Create a Lightning invoice */
    suspend fun makeInvoice(amountMsats: Long, description: String = ""): Response<String>

    /** Get wallet balance in millisatoshis */
    suspend fun getBalance(): Response<Long>

    /** Get wallet info (supported methods) */
    suspend fun getInfo(): Response<List<String>>

    /** List transactions */
    suspend fun listTransactions(since: Long? = null, until: Long? = null, limit: Int = 20): Response<List<NwcTransaction>>

    /** Disconnect from the wallet service */
    suspend fun disconnect()

    @JsExport.Ignore
    fun connectBlocking(nwcUrl: String)

    @JsExport.Ignore
    fun payInvoiceBlocking(invoice: String): Response<String>

    @JsExport.Ignore
    fun makeInvoiceBlocking(amountMsats: Long, description: String = ""): Response<String>

    @JsExport.Ignore
    fun getBalanceBlocking(): Response<Long>

    @JsExport.Ignore
    fun getInfoBlocking(): Response<List<String>>

    @JsExport.Ignore
    fun listTransactionsBlocking(since: Long? = null, until: Long? = null, limit: Int = 20): Response<List<NwcTransaction>>

    @JsExport.Ignore
    fun disconnectBlocking()
}
