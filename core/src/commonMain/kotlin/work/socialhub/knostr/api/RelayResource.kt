package work.socialhub.knostr.api

import kotlin.js.JsExport

@JsExport
interface RelayResource {

    /** Connect to all configured relays */
    suspend fun connect()

    /** Disconnect from all relays */
    suspend fun disconnect()

    /** Get list of currently connected relay URLs */
    fun getConnectedRelays(): List<String>

    @JsExport.Ignore
    fun connectBlocking()

    @JsExport.Ignore
    fun disconnectBlocking()
}
