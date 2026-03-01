package work.socialhub.knostr.social.internal

import work.socialhub.knostr.EventKind
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrProfile
import work.socialhub.knostr.internal.InternalUtility
import work.socialhub.knostr.social.model.NostrNote
import work.socialhub.knostr.social.model.NostrReaction
import work.socialhub.knostr.social.model.NostrUser
import work.socialhub.knostr.social.model.NostrZap
import work.socialhub.knostr.util.Bech32
import work.socialhub.knostr.util.Hex

/**
 * Maps core NostrEvent objects to social model objects.
 */
object SocialMapper {

    /** Map a kind:0 event to NostrUser */
    fun toUser(event: NostrEvent): NostrUser {
        val profile = try {
            InternalUtility.fromJson<NostrProfile>(event.content)
        } catch (_: Exception) {
            NostrProfile()
        }

        return NostrUser().apply {
            pubkey = event.pubkey
            npub = Bech32.encode("npub", Hex.decode(event.pubkey))
            name = profile.name
            displayName = profile.displayName
            about = profile.about
            picture = profile.picture
            banner = profile.banner
            nip05 = profile.nip05
            website = profile.website
            lud16 = profile.lud16
        }
    }

    /** Map a kind:1 event to NostrNote */
    fun toNote(event: NostrEvent): NostrNote {
        return NostrNote().apply {
            this.event = event
            content = event.content
            createdAt = event.createdAt
            noteId = Bech32.encode("note", Hex.decode(event.id))

            // NIP-36: content warning
            contentWarning = event.tags
                .firstOrNull { it.size >= 2 && it[0] == "content-warning" }
                ?.get(1)

            // NIP-18: quote repost (q tag)
            quotedEventId = event.tags
                .firstOrNull { it.size >= 2 && it[0] == "q" }
                ?.get(1)

            // Parse NIP-10 reply threading from e-tags
            for (tag in event.tags) {
                if (tag.size >= 2 && tag[0] == "e") {
                    val marker = if (tag.size >= 4) tag[3] else null
                    when (marker) {
                        "root" -> rootEventId = tag[1]
                        "reply" -> replyToEventId = tag[1]
                        else -> {
                            // Legacy: positional e-tags (first = root, last = reply)
                            if (rootEventId == null) {
                                rootEventId = tag[1]
                            }
                            replyToEventId = tag[1]
                        }
                    }
                }
            }
        }
    }

    /** Map a kind:7 event to NostrReaction */
    fun toReaction(event: NostrEvent): NostrReaction {
        return NostrReaction().apply {
            this.event = event
            content = event.content.ifEmpty { "+" }
            createdAt = event.createdAt

            // Find the target event from e-tags
            for (tag in event.tags) {
                if (tag.size >= 2 && tag[0] == "e") {
                    targetEventId = tag[1]
                }
            }
        }
    }

    /** Map a kind:9735 event to NostrZap */
    fun toZap(event: NostrEvent): NostrZap? {
        return try {
            val zap = NostrZap()
            zap.event = event
            zap.createdAt = event.createdAt

            for (tag in event.tags) {
                if (tag.size < 2) continue
                when (tag[0]) {
                    "p" -> zap.recipientPubkey = tag[1]
                    "e" -> zap.targetEventId = tag[1]
                    "bolt11" -> {
                        // Extract amount from bolt11 invoice
                        zap.amountMilliSats = parseBolt11Amount(tag[1])
                    }
                    "description" -> {
                        // The zap request event is embedded in the description tag
                        try {
                            val zapRequest = InternalUtility.fromJson<NostrEvent>(tag[1])
                            zap.message = zapRequest.content
                        } catch (_: Exception) {
                        }
                    }
                }
            }
            zap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parse amount from a bolt11 invoice string.
     * bolt11 invoices encode amount after "lnbc" prefix.
     * e.g., "lnbc10n..." = 10 sats, "lnbc1m..." = 0.001 BTC
     */
    private fun parseBolt11Amount(bolt11: String): Long {
        val lower = bolt11.lowercase()
        if (!lower.startsWith("lnbc")) return 0

        val amountStr = lower.removePrefix("lnbc")
        val multiplierIdx = amountStr.indexOfFirst { it.isLetter() }
        if (multiplierIdx <= 0) return 0

        val numberStr = amountStr.substring(0, multiplierIdx)
        val multiplier = amountStr[multiplierIdx]
        val number = numberStr.toLongOrNull() ?: return 0

        // Convert to millisatoshis
        return when (multiplier) {
            'm' -> number * 100_000_000L    // milli-BTC -> msats
            'u' -> number * 100_000L         // micro-BTC -> msats
            'n' -> number * 100L             // nano-BTC -> msats
            'p' -> number / 10L              // pico-BTC -> msats
            else -> 0
        }
    }

    /** Extract follow list (pubkeys) from a kind:3 event */
    fun toFollowList(event: NostrEvent): List<String> {
        return event.tags
            .filter { it.size >= 2 && it[0] == "p" }
            .map { it[1] }
    }
}
