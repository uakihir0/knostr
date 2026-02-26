package work.socialhub.knostr

import work.socialhub.knostr.internal.NostrImpl
import work.socialhub.knostr.signing.createSigner
import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
object NostrFactory {

    /** Create with full config */
    @JsName("instanceFromConfig")
    fun instance(config: NostrConfig): Nostr {
        return NostrImpl(config)
    }

    /** Create with private key and relay list */
    @JsExport.Ignore
    fun instance(privateKeyHex: String, relays: List<String>): Nostr {
        return NostrImpl(NostrConfig().also {
            it.signer = createSigner(privateKeyHex)
            it.relayUrls = relays
        })
    }

    /** Create read-only (no signing capability) */
    @JsExport.Ignore
    fun instance(relays: List<String>): Nostr {
        return NostrImpl(NostrConfig().also {
            it.relayUrls = relays
        })
    }
}
