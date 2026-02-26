package work.socialhub.knostr.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.Nip05Result
import work.socialhub.knostr.entity.Nip19Entity
import kotlin.js.JsExport

@JsExport
interface NipResource {

    // NIP-05: DNS-based identity verification
    /** Resolve a NIP-05 address (e.g., "alice@example.com") */
    suspend fun resolveNip05(address: String): Response<Nip05Result>

    @JsExport.Ignore
    fun resolveNip05Blocking(address: String): Response<Nip05Result>

    // NIP-19: bech32 encoding
    /** Encode a public key as npub */
    fun encodeNpub(pubkey: String): String

    /** Encode a secret key as nsec */
    fun encodeNsec(seckey: String): String

    /** Encode an event ID as note */
    fun encodeNote(eventId: String): String

    /** Decode a NIP-19 bech32 string */
    fun decodeNip19(encoded: String): Nip19Entity
}
