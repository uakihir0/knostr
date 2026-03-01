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

    // NIP-19 TLV types
    private companion object {
        const val TLV_SPECIAL = 0
        const val TLV_RELAY = 1
        const val TLV_AUTHOR = 2
        const val TLV_KIND = 3
    }

    override fun encodeNprofile(pubkey: String, relays: List<String>): String {
        val tlv = mutableListOf<Byte>()
        writeTlv(tlv, TLV_SPECIAL, Hex.decode(pubkey))
        for (relay in relays) {
            writeTlv(tlv, TLV_RELAY, relay.encodeToByteArray())
        }
        return Bech32.encode("nprofile", tlv.toByteArray())
    }

    override fun encodeNevent(eventId: String, relays: List<String>, author: String?): String {
        val tlv = mutableListOf<Byte>()
        writeTlv(tlv, TLV_SPECIAL, Hex.decode(eventId))
        for (relay in relays) {
            writeTlv(tlv, TLV_RELAY, relay.encodeToByteArray())
        }
        if (author != null) {
            writeTlv(tlv, TLV_AUTHOR, Hex.decode(author))
        }
        return Bech32.encode("nevent", tlv.toByteArray())
    }

    override fun encodeNaddr(identifier: String, pubkey: String, kind: Int, relays: List<String>): String {
        val tlv = mutableListOf<Byte>()
        writeTlv(tlv, TLV_SPECIAL, identifier.encodeToByteArray())
        for (relay in relays) {
            writeTlv(tlv, TLV_RELAY, relay.encodeToByteArray())
        }
        writeTlv(tlv, TLV_AUTHOR, Hex.decode(pubkey))
        val kindBytes = ByteArray(4)
        kindBytes[0] = ((kind shr 24) and 0xFF).toByte()
        kindBytes[1] = ((kind shr 16) and 0xFF).toByte()
        kindBytes[2] = ((kind shr 8) and 0xFF).toByte()
        kindBytes[3] = (kind and 0xFF).toByte()
        writeTlv(tlv, TLV_KIND, kindBytes)
        return Bech32.encode("naddr", tlv.toByteArray())
    }

    override fun decodeNip19(encoded: String): Nip19Entity {
        val (hrp, data) = Bech32.decode(encoded)

        return when (hrp) {
            "npub" -> Nip19Entity.NPub(Hex.encode(data))
            "nsec" -> Nip19Entity.NSec(Hex.encode(data))
            "note" -> Nip19Entity.Note(Hex.encode(data))
            "nprofile" -> {
                val tlvs = parseTlv(data)
                val pubkey = Hex.encode(tlvs[TLV_SPECIAL]?.firstOrNull() ?: throw NostrException("Missing pubkey in nprofile"))
                val relays = tlvs[TLV_RELAY]?.map { it.decodeToString() } ?: listOf()
                Nip19Entity.NProfile(pubkey, relays)
            }
            "nevent" -> {
                val tlvs = parseTlv(data)
                val eventId = Hex.encode(tlvs[TLV_SPECIAL]?.firstOrNull() ?: throw NostrException("Missing eventId in nevent"))
                val relays = tlvs[TLV_RELAY]?.map { it.decodeToString() } ?: listOf()
                val author = tlvs[TLV_AUTHOR]?.firstOrNull()?.let { Hex.encode(it) }
                Nip19Entity.NEvent(eventId, relays, author)
            }
            "naddr" -> {
                val tlvs = parseTlv(data)
                val identifier = tlvs[TLV_SPECIAL]?.firstOrNull()?.decodeToString() ?: ""
                val relays = tlvs[TLV_RELAY]?.map { it.decodeToString() } ?: listOf()
                val pubkey = Hex.encode(tlvs[TLV_AUTHOR]?.firstOrNull() ?: throw NostrException("Missing pubkey in naddr"))
                val kindBytes = tlvs[TLV_KIND]?.firstOrNull() ?: throw NostrException("Missing kind in naddr")
                val kind = ((kindBytes[0].toInt() and 0xFF) shl 24) or
                    ((kindBytes[1].toInt() and 0xFF) shl 16) or
                    ((kindBytes[2].toInt() and 0xFF) shl 8) or
                    (kindBytes[3].toInt() and 0xFF)
                Nip19Entity.NAddr(identifier, pubkey, kind, relays)
            }
            else -> throw NostrException("Unknown NIP-19 prefix: $hrp")
        }
    }

    private fun writeTlv(buffer: MutableList<Byte>, type: Int, value: ByteArray) {
        buffer.add(type.toByte())
        buffer.add(value.size.toByte())
        for (b in value) {
            buffer.add(b)
        }
    }

    private fun parseTlv(data: ByteArray): Map<Int, MutableList<ByteArray>> {
        val result = mutableMapOf<Int, MutableList<ByteArray>>()
        var i = 0
        while (i < data.size) {
            if (i + 1 >= data.size) break
            val type = data[i].toInt() and 0xFF
            val length = data[i + 1].toInt() and 0xFF
            i += 2
            if (i + length > data.size) break
            val value = data.copyOfRange(i, i + length)
            result.getOrPut(type) { mutableListOf() }.add(value)
            i += length
        }
        return result
    }
}
