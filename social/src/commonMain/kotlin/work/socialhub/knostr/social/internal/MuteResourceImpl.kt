package work.socialhub.knostr.social.internal

import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.social.api.MuteResource
import work.socialhub.knostr.util.toBlocking
import kotlin.time.Clock

class MuteResourceImpl(
    private val nostr: Nostr,
) : MuteResource {

    override suspend fun mute(pubkey: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to mute")

        val currentTags = getMuteTags()

        // Already muted — return a no-op by re-publishing current list
        val tags = currentTags.toMutableList()
        if (tags.none { it.size >= 2 && it[0] == "p" && it[1] == pubkey }) {
            tags.add(listOf("p", pubkey))
        }

        return publishMuteList(signer, tags)
    }

    override suspend fun unmute(pubkey: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to unmute")

        val currentTags = getMuteTags()
        val tags = currentTags.filter { !(it.size >= 2 && it[0] == "p" && it[1] == pubkey) }

        return publishMuteList(signer, tags)
    }

    override suspend fun getMuteList(): Response<List<String>> {
        val tags = getMuteTags()
        val pubkeys = tags
            .filter { it.size >= 2 && it[0] == "p" }
            .map { it[1] }
        return Response(pubkeys)
    }

    private suspend fun getMuteTags(): List<List<String>> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to get mute list")

        val filter = NostrFilter(
            authors = listOf(signer.getPublicKey()),
            kinds = listOf(EventKind.MUTE_LIST),
            limit = 1,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        return response.data.firstOrNull()?.tags ?: listOf()
    }

    private suspend fun publishMuteList(
        signer: work.socialhub.knostr.signing.NostrSigner,
        tags: List<List<String>>,
    ): Response<NostrEvent> {
        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.MUTE_LIST,
            tags = tags,
            content = "",
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override fun muteBlocking(pubkey: String): Response<NostrEvent> {
        return toBlocking { mute(pubkey) }
    }

    override fun unmuteBlocking(pubkey: String): Response<NostrEvent> {
        return toBlocking { unmute(pubkey) }
    }

    override fun getMuteListBlocking(): Response<List<String>> {
        return toBlocking { getMuteList() }
    }
}
