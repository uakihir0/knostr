package work.socialhub.knostr.internal

import work.socialhub.knostr.NostrConfig
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.NipResource
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.Nip05Result
import work.socialhub.knostr.entity.Nip19Entity
import work.socialhub.knostr.util.Bech32
import work.socialhub.knostr.util.Hex
import work.socialhub.knostr.util.toBlocking
import work.socialhub.khttpclient.HttpRequest

class NipResourceImpl(
    private val config: NostrConfig,
) : NipResource {

    // NIP-05: DNS-based identity verification

    override suspend fun resolveNip05(address: String): Response<Nip05Result> {
        try {
            val parts = address.split("@")
            require(parts.size == 2) { "Invalid NIP-05 address format: $address" }
            val name = parts[0]
            val domain = parts[1]

            val url = "https://$domain/.well-known/nostr.json?name=$name"
            val response = HttpRequest()
                .url(url)
                .get()

            val result = InternalUtility.fromJson<Nip05Result>(response.stringBody)
            return Response(result).also {
                it.json = response.stringBody
            }
        } catch (e: Exception) {
            throw NostrException(e)
        }
    }

    override fun resolveNip05Blocking(address: String): Response<Nip05Result> {
        return toBlocking { resolveNip05(address) }
    }

    // NIP-19: bech32 encoding

    override fun encodeNpub(pubkey: String): String {
        return Bech32.encode("npub", Hex.decode(pubkey))
    }

    override fun encodeNsec(seckey: String): String {
        return Bech32.encode("nsec", Hex.decode(seckey))
    }

    override fun encodeNote(eventId: String): String {
        return Bech32.encode("note", Hex.decode(eventId))
    }

    override fun decodeNip19(encoded: String): Nip19Entity {
        val (hrp, data) = Bech32.decode(encoded)
        val hex = Hex.encode(data)

        return when (hrp) {
            "npub" -> Nip19Entity.NPub(hex)
            "nsec" -> Nip19Entity.NSec(hex)
            "note" -> Nip19Entity.Note(hex)
            else -> throw NostrException("Unknown NIP-19 prefix: $hrp")
        }
    }
}
