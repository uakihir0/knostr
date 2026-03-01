package work.socialhub.knostr.social.internal

import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.NostrProfile
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.internal.InternalUtility
import work.socialhub.knostr.social.api.UserResource
import work.socialhub.knostr.social.model.NostrUser
import work.socialhub.knostr.social.model.NostrUserStatus
import work.socialhub.knostr.util.Bech32
import work.socialhub.knostr.util.Hex
import work.socialhub.knostr.util.toBlocking
import kotlin.time.Clock

class UserResourceImpl(
    private val nostr: Nostr,
) : UserResource {

    override suspend fun getProfile(pubkey: String): Response<NostrUser> {
        val filter = NostrFilter(
            authors = listOf(pubkey),
            kinds = listOf(EventKind.METADATA),
            limit = 1,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val event = response.data.firstOrNull()
        if (event == null) {
            // Return minimal user with just pubkey when profile not found
            val user = NostrUser().apply {
                this.pubkey = pubkey
                this.npub = Bech32.encode("npub", Hex.decode(pubkey))
            }
            return Response(user)
        }

        return Response(SocialMapper.toUser(event))
    }

    override suspend fun updateProfile(profile: NostrProfile): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to update profile")

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.METADATA,
            content = InternalUtility.toJson(profile),
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun follow(pubkey: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to follow")

        // Get current follow list
        val currentFollowing = getFollowingTags(signer.getPublicKey())

        // Add the new pubkey if not already following
        val tags = currentFollowing.toMutableList()
        if (tags.none { it.size >= 2 && it[1] == pubkey }) {
            tags.add(listOf("p", pubkey))
        }

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.FOLLOW_LIST,
            tags = tags,
            content = "",
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun unfollow(pubkey: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to unfollow")

        // Get current follow list and remove the pubkey
        val currentFollowing = getFollowingTags(signer.getPublicKey())
        val tags = currentFollowing.filter { !(it.size >= 2 && it[0] == "p" && it[1] == pubkey) }

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.FOLLOW_LIST,
            tags = tags,
            content = "",
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun getFollowing(pubkey: String): Response<List<String>> {
        val filter = NostrFilter(
            authors = listOf(pubkey),
            kinds = listOf(EventKind.FOLLOW_LIST),
            limit = 1,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val followList = response.data.firstOrNull()
            ?.let { SocialMapper.toFollowList(it) }
            ?: listOf()

        return Response(followList)
    }

    override suspend fun getFollowers(pubkey: String, limit: Int): Response<List<String>> {
        val filter = NostrFilter(
            pTags = listOf(pubkey),
            kinds = listOf(EventKind.FOLLOW_LIST),
            limit = limit,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        // Each matching event's author follows the target pubkey
        // Deduplicate (one per author, latest wins)
        val followers = response.data
            .sortedByDescending { it.createdAt }
            .distinctBy { it.pubkey }
            .map { it.pubkey }
        return Response(followers)
    }

    override suspend fun getProfiles(pubkeys: List<String>): Response<List<NostrUser>> {
        if (pubkeys.isEmpty()) return Response(listOf())

        val filter = NostrFilter(
            authors = pubkeys,
            kinds = listOf(EventKind.METADATA),
        )
        val response = nostr.events().queryEvents(listOf(filter))
        // Take latest metadata per pubkey
        val users = response.data
            .sortedByDescending { it.createdAt }
            .distinctBy { it.pubkey }
            .map { SocialMapper.toUser(it) }
        return Response(users)
    }

    override suspend fun verifyNip05(address: String): Response<Boolean> {
        return try {
            val result = nostr.nip().resolveNip05(address)
            val parts = address.split("@")
            val name = parts[0]
            val verified = result.data.names.containsKey(name)
            Response(verified)
        } catch (_: Exception) {
            Response(false)
        }
    }

    override suspend fun setStatus(content: String, type: String, url: String?, expiration: Long?): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to set status")

        val tags = mutableListOf<List<String>>()
        tags.add(listOf("d", type))
        if (url != null) {
            tags.add(listOf("r", url))
        }
        if (expiration != null) {
            tags.add(listOf("expiration", expiration.toString()))
        }

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.USER_STATUS,
            tags = tags,
            content = content,
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun getStatus(pubkey: String, type: String): Response<NostrUserStatus?> {
        val filter = NostrFilter(
            authors = listOf(pubkey),
            kinds = listOf(EventKind.USER_STATUS),
            dTags = listOf(type),
            limit = 1,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val event = response.data.firstOrNull()
        if (event == null) {
            return Response(null)
        }

        val statusUrl = event.tags.firstOrNull { it.size >= 2 && it[0] == "r" }?.get(1)
        val expiration = event.tags.firstOrNull { it.size >= 2 && it[0] == "expiration" }?.get(1)?.toLongOrNull()

        return Response(NostrUserStatus(
            type = type,
            content = event.content,
            url = statusUrl,
            expiration = expiration,
        ))
    }

    override suspend fun clearStatus(type: String): Response<NostrEvent> {
        return setStatus("", type)
    }

    private suspend fun getFollowingTags(pubkey: String): List<List<String>> {
        val filter = NostrFilter(
            authors = listOf(pubkey),
            kinds = listOf(EventKind.FOLLOW_LIST),
            limit = 1,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        return response.data.firstOrNull()?.tags ?: listOf()
    }

    override fun getProfileBlocking(pubkey: String): Response<NostrUser> {
        return toBlocking { getProfile(pubkey) }
    }

    override fun updateProfileBlocking(profile: NostrProfile): Response<NostrEvent> {
        return toBlocking { updateProfile(profile) }
    }

    override fun followBlocking(pubkey: String): Response<NostrEvent> {
        return toBlocking { follow(pubkey) }
    }

    override fun unfollowBlocking(pubkey: String): Response<NostrEvent> {
        return toBlocking { unfollow(pubkey) }
    }

    override fun getFollowingBlocking(pubkey: String): Response<List<String>> {
        return toBlocking { getFollowing(pubkey) }
    }

    override fun getFollowersBlocking(pubkey: String, limit: Int): Response<List<String>> {
        return toBlocking { getFollowers(pubkey, limit) }
    }

    override fun getProfilesBlocking(pubkeys: List<String>): Response<List<NostrUser>> {
        return toBlocking { getProfiles(pubkeys) }
    }

    override fun verifyNip05Blocking(address: String): Response<Boolean> {
        return toBlocking { verifyNip05(address) }
    }

    override fun setStatusBlocking(content: String, type: String, url: String?, expiration: Long?): Response<NostrEvent> {
        return toBlocking { setStatus(content, type, url, expiration) }
    }

    override fun getStatusBlocking(pubkey: String, type: String): Response<NostrUserStatus?> {
        return toBlocking { getStatus(pubkey, type) }
    }

    override fun clearStatusBlocking(type: String): Response<NostrEvent> {
        return toBlocking { clearStatus(type) }
    }
}
