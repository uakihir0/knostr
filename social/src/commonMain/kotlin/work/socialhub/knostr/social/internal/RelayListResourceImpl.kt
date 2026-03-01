package work.socialhub.knostr.social.internal

import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.social.api.RelayListResource
import work.socialhub.knostr.social.model.NostrRelayListEntry
import work.socialhub.knostr.util.toBlocking
import kotlin.time.Clock

class RelayListResourceImpl(
    private val nostr: Nostr,
) : RelayListResource {

    override suspend fun getRelayList(): Response<List<NostrRelayListEntry>> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to get own relay list")
        return getRelayList(signer.getPublicKey())
    }

    override suspend fun getRelayList(pubkey: String): Response<List<NostrRelayListEntry>> {
        val filter = NostrFilter(
            authors = listOf(pubkey),
            kinds = listOf(EventKind.RELAY_LIST),
            limit = 1,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val event = response.data.firstOrNull()
        val entries = event?.tags
            ?.filter { it.size >= 2 && it[0] == "r" }
            ?.map { tag ->
                val url = tag[1]
                val marker = if (tag.size >= 3) tag[2] else null
                when (marker) {
                    "read" -> NostrRelayListEntry(url, read = true, write = false)
                    "write" -> NostrRelayListEntry(url, read = false, write = true)
                    else -> NostrRelayListEntry(url, read = true, write = true)
                }
            } ?: listOf()
        return Response(entries)
    }

    override suspend fun setRelayList(relays: List<NostrRelayListEntry>): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to set relay list")

        val tags = relays.map { entry ->
            when {
                entry.read && entry.write -> listOf("r", entry.url)
                entry.read -> listOf("r", entry.url, "read")
                entry.write -> listOf("r", entry.url, "write")
                else -> listOf("r", entry.url)
            }
        }

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.RELAY_LIST,
            tags = tags,
            content = "",
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override fun getRelayListBlocking(): Response<List<NostrRelayListEntry>> {
        return toBlocking { getRelayList() }
    }

    override fun getRelayListBlocking(pubkey: String): Response<List<NostrRelayListEntry>> {
        return toBlocking { getRelayList(pubkey) }
    }

    override fun setRelayListBlocking(relays: List<NostrRelayListEntry>): Response<NostrEvent> {
        return toBlocking { setRelayList(relays) }
    }
}
