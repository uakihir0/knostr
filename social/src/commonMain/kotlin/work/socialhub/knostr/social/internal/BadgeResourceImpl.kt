package work.socialhub.knostr.social.internal

import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.social.api.BadgeResource
import work.socialhub.knostr.social.model.NostrBadge
import work.socialhub.knostr.util.toBlocking
import kotlin.time.Clock

class BadgeResourceImpl(
    private val nostr: Nostr,
) : BadgeResource {

    override suspend fun defineBadge(
        dTag: String,
        name: String,
        description: String,
        image: String,
        thumbImage: String,
    ): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to define badge")

        val tags = mutableListOf(
            listOf("d", dTag),
            listOf("name", name),
        )
        if (description.isNotEmpty()) tags.add(listOf("description", description))
        if (image.isNotEmpty()) tags.add(listOf("image", image))
        if (thumbImage.isNotEmpty()) tags.add(listOf("thumb", thumbImage))

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.BADGE_DEFINITION,
            tags = tags,
            content = "",
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun awardBadge(
        badgeDTag: String,
        recipientPubkeys: List<String>,
    ): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to award badge")

        val tags = mutableListOf(
            listOf("a", "${EventKind.BADGE_DEFINITION}:${signer.getPublicKey()}:$badgeDTag"),
        )
        for (pubkey in recipientPubkeys) {
            tags.add(listOf("p", pubkey))
        }

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.BADGE_AWARD,
            tags = tags,
            content = "",
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun setProfileBadges(
        badgeRefs: List<Pair<String, String>>,
    ): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to set profile badges")

        val tags = mutableListOf(listOf("d", "profile_badges"))
        for ((aTag, eventId) in badgeRefs) {
            tags.add(listOf("a", aTag))
            tags.add(listOf("e", eventId))
        }

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.PROFILE_BADGES,
            tags = tags,
            content = "",
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun getBadgeDefinition(pubkey: String, dTag: String): Response<NostrBadge> {
        val filter = NostrFilter(
            authors = listOf(pubkey),
            kinds = listOf(EventKind.BADGE_DEFINITION),
            limit = 10,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val event = response.data
            .filter { e -> e.tags.any { it.size >= 2 && it[0] == "d" && it[1] == dTag } }
            .maxByOrNull { it.createdAt }
            ?: throw NostrException("Badge not found: $dTag")

        return Response(parseBadgeFromEvent(event))
    }

    override suspend fun getProfileBadges(pubkey: String): Response<List<NostrBadge>> {
        val filter = NostrFilter(
            authors = listOf(pubkey),
            kinds = listOf(EventKind.PROFILE_BADGES),
            limit = 1,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val profileEvent = response.data.firstOrNull()
            ?: return Response(listOf())

        // Extract badge definition a-tags
        val aTags = profileEvent.tags
            .filter { it.size >= 2 && it[0] == "a" }
            .map { it[1] }

        // Fetch each badge definition
        val badges = mutableListOf<NostrBadge>()
        for (aTag in aTags) {
            val parts = aTag.split(":")
            if (parts.size >= 3 && parts[0] == EventKind.BADGE_DEFINITION.toString()) {
                try {
                    val badge = getBadgeDefinition(parts[1], parts[2]).data
                    badges.add(badge)
                } catch (_: Exception) {
                    // Skip badges that can't be fetched
                }
            }
        }

        return Response(badges)
    }

    private fun parseBadgeFromEvent(event: NostrEvent): NostrBadge {
        val badge = NostrBadge()
        badge.event = event
        badge.createdAt = event.createdAt

        for (tag in event.tags) {
            if (tag.size < 2) continue
            when (tag[0]) {
                "d" -> badge.dTag = tag[1]
                "name" -> badge.name = tag[1]
                "description" -> badge.description = tag[1]
                "image" -> badge.image = tag[1]
                "thumb" -> badge.thumbImage = tag[1]
            }
        }

        return badge
    }

    override fun defineBadgeBlocking(dTag: String, name: String, description: String, image: String, thumbImage: String): Response<NostrEvent> {
        return toBlocking { defineBadge(dTag, name, description, image, thumbImage) }
    }

    override fun awardBadgeBlocking(badgeDTag: String, recipientPubkeys: List<String>): Response<NostrEvent> {
        return toBlocking { awardBadge(badgeDTag, recipientPubkeys) }
    }

    override fun setProfileBadgesBlocking(badgeRefs: List<Pair<String, String>>): Response<NostrEvent> {
        return toBlocking { setProfileBadges(badgeRefs) }
    }

    override fun getBadgeDefinitionBlocking(pubkey: String, dTag: String): Response<NostrBadge> {
        return toBlocking { getBadgeDefinition(pubkey, dTag) }
    }

    override fun getProfileBadgesBlocking(pubkey: String): Response<List<NostrBadge>> {
        return toBlocking { getProfileBadges(pubkey) }
    }
}
