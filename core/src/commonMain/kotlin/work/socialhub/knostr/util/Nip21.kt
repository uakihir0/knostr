package work.socialhub.knostr.util

/**
 * NIP-21 `nostr:` URI parsing helpers.
 *
 * Extracts event references (`nostr:note1...`, `nostr:nevent1...`) embedded in
 * event content. Used as a fallback to recover quote targets when an event has
 * no explicit NIP-18 `q` tag but mentions the quoted event inline.
 */
object Nip21 {

    // NIP-19 TLV type for the primary value (event id for nevent).
    private const val TLV_SPECIAL = 0

    // `nostr:` prefix followed by a note/nevent bech32 token.
    // The bech32 body is matched loosely; Bech32.decode validates the checksum.
    private val EVENT_REFERENCE = Regex("nostr:((?:note|nevent)1[ac-hj-np-z02-9]+)")

    /**
     * Extract every event id (hex) referenced via `nostr:note1...` or
     * `nostr:nevent1...` in the given content, in order of appearance.
     * Invalid or undecodable references are skipped.
     */
    fun extractEventIds(content: String): List<String> {
        return EVENT_REFERENCE.findAll(content)
            .mapNotNull { decodeEventId(it.groupValues[1]) }
            .toList()
    }

    /**
     * Extract the first event id (hex) referenced via a `nostr:` URI, or null
     * if the content contains no decodable note/nevent reference.
     */
    fun firstEventId(content: String): String? {
        return EVENT_REFERENCE.findAll(content)
            .firstNotNullOfOrNull { decodeEventId(it.groupValues[1]) }
    }

    /** Decode a bare `note1`/`nevent1` token into a hex event id, or null on failure. */
    private fun decodeEventId(token: String): String? {
        return try {
            val (hrp, data) = Bech32.decode(token)
            when (hrp) {
                // note: data is the raw 32-byte event id.
                "note" -> Hex.encode(data)
                // nevent: data is TLV; the event id is the TLV_SPECIAL entry.
                "nevent" -> tlvSpecial(data)?.let { Hex.encode(it) }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Read the first TLV_SPECIAL (type 0) value out of a NIP-19 TLV payload. */
    private fun tlvSpecial(data: ByteArray): ByteArray? {
        var i = 0
        while (i + 1 < data.size) {
            val type = data[i].toInt() and 0xFF
            val length = data[i + 1].toInt() and 0xFF
            i += 2
            if (i + length > data.size) break
            if (type == TLV_SPECIAL) {
                return data.copyOfRange(i, i + length)
            }
            i += length
        }
        return null
    }
}
