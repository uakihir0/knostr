package work.socialhub.knostr.social.internal

import work.socialhub.knostr.EventKind
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrProfile
import work.socialhub.knostr.internal.InternalUtility
import work.socialhub.knostr.social.model.NostrNote
import work.socialhub.knostr.social.model.NostrReaction
import work.socialhub.knostr.social.model.NostrUser
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

    /** Extract follow list (pubkeys) from a kind:3 event */
    fun toFollowList(event: NostrEvent): List<String> {
        return event.tags
            .filter { it.size >= 2 && it[0] == "p" }
            .map { it[1] }
    }
}
