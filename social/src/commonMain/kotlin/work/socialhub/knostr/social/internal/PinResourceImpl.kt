package work.socialhub.knostr.social.internal

import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.signing.NostrSigner
import work.socialhub.knostr.social.api.PinResource
import work.socialhub.knostr.util.toBlocking
import kotlin.time.Clock

class PinResourceImpl(
    private val nostr: Nostr,
) : PinResource {

    override suspend fun pin(eventId: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to pin")

        val currentTags = getPinTags()
        val tags = currentTags.toMutableList()
        if (tags.none { it.size >= 2 && it[0] == "e" && it[1] == eventId }) {
            tags.add(listOf("e", eventId))
        }

        return publishPinList(signer, tags)
    }

    override suspend fun unpin(eventId: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to unpin")

        val currentTags = getPinTags()
        val tags = currentTags.filter { !(it.size >= 2 && it[0] == "e" && it[1] == eventId) }

        return publishPinList(signer, tags)
    }

    override suspend fun getPinList(): Response<List<String>> {
        val tags = getPinTags()
        val eventIds = tags
            .filter { it.size >= 2 && it[0] == "e" }
            .map { it[1] }
        return Response(eventIds)
    }

    private suspend fun getPinTags(): List<List<String>> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to get pin list")

        val filter = NostrFilter(
            authors = listOf(signer.getPublicKey()),
            kinds = listOf(EventKind.PIN_LIST),
            limit = 1,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        return response.data.firstOrNull()?.tags ?: listOf()
    }

    private suspend fun publishPinList(
        signer: NostrSigner,
        tags: List<List<String>>,
    ): Response<NostrEvent> {
        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.PIN_LIST,
            tags = tags,
            content = "",
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override fun pinBlocking(eventId: String): Response<NostrEvent> {
        return toBlocking { pin(eventId) }
    }

    override fun unpinBlocking(eventId: String): Response<NostrEvent> {
        return toBlocking { unpin(eventId) }
    }

    override fun getPinListBlocking(): Response<List<String>> {
        return toBlocking { getPinList() }
    }
}
