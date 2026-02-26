package work.socialhub.knostr

import work.socialhub.knostr.api.EventResource
import work.socialhub.knostr.api.NipResource
import work.socialhub.knostr.api.RelayResource
import work.socialhub.knostr.relay.RelayPool
import work.socialhub.knostr.signing.NostrSigner
import kotlin.js.JsExport

@JsExport
interface Nostr {
    fun events(): EventResource
    fun relays(): RelayResource
    fun nip(): NipResource
    fun signer(): NostrSigner?
    fun config(): NostrConfig
    fun relayPool(): RelayPool
}
