package work.socialhub.knostr.social.internal

import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.signing.NostrSigner
import work.socialhub.knostr.social.api.ListResource
import work.socialhub.knostr.social.model.NostrList
import work.socialhub.knostr.util.toBlocking
import kotlin.time.Clock

class ListResourceImpl(
    private val nostr: Nostr,
) : ListResource {

    override suspend fun createList(name: String, pubkeys: List<String>): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to create list")

        val tags = mutableListOf<List<String>>()
        tags.add(listOf("d", name))
        for (pubkey in pubkeys) {
            tags.add(listOf("p", pubkey))
        }

        return publishList(signer, tags)
    }

    override suspend fun addToList(name: String, pubkey: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to add to list")

        val currentTags = getListTags(name)
        val tags = currentTags.toMutableList()
        if (tags.none { it.size >= 2 && it[0] == "p" && it[1] == pubkey }) {
            tags.add(listOf("p", pubkey))
        }
        // Ensure d-tag exists
        if (tags.none { it.size >= 2 && it[0] == "d" }) {
            tags.add(0, listOf("d", name))
        }

        return publishList(signer, tags)
    }

    override suspend fun removeFromList(name: String, pubkey: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to remove from list")

        val currentTags = getListTags(name)
        val tags = currentTags.filter { !(it.size >= 2 && it[0] == "p" && it[1] == pubkey) }
        // Ensure d-tag exists
        val finalTags = if (tags.none { it.size >= 2 && it[0] == "d" }) {
            listOf(listOf("d", name)) + tags
        } else {
            tags
        }

        return publishList(signer, finalTags)
    }

    override suspend fun getList(name: String): Response<NostrList> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to get own list")

        val filter = NostrFilter(
            authors = listOf(signer.getPublicKey()),
            kinds = listOf(EventKind.PEOPLE_LIST),
            dTags = listOf(name),
            limit = 1,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val event = response.data.firstOrNull()
            ?: throw NostrException("List not found: $name")
        return Response(toList(event))
    }

    override suspend fun getLists(): Response<List<NostrList>> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to get own lists")
        return getLists(signer.getPublicKey())
    }

    override suspend fun getLists(pubkey: String): Response<List<NostrList>> {
        val filter = NostrFilter(
            authors = listOf(pubkey),
            kinds = listOf(EventKind.PEOPLE_LIST),
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val lists = response.data
            .sortedByDescending { it.createdAt }
            .distinctBy { e -> e.tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.get(1) }
            .map { toList(it) }
        return Response(lists)
    }

    private suspend fun getListTags(name: String): List<List<String>> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to get list")

        val filter = NostrFilter(
            authors = listOf(signer.getPublicKey()),
            kinds = listOf(EventKind.PEOPLE_LIST),
            dTags = listOf(name),
            limit = 1,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        return response.data.firstOrNull()?.tags ?: listOf()
    }

    private suspend fun publishList(
        signer: NostrSigner,
        tags: List<List<String>>,
    ): Response<NostrEvent> {
        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.PEOPLE_LIST,
            tags = tags,
            content = "",
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    private fun toList(event: NostrEvent): NostrList {
        val list = NostrList()
        list.event = event
        list.createdAt = event.createdAt
        list.name = event.tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.get(1) ?: ""
        list.pubkeys = event.tags
            .filter { it.size >= 2 && it[0] == "p" }
            .map { it[1] }
        return list
    }

    override fun createListBlocking(name: String, pubkeys: List<String>): Response<NostrEvent> {
        return toBlocking { createList(name, pubkeys) }
    }

    override fun addToListBlocking(name: String, pubkey: String): Response<NostrEvent> {
        return toBlocking { addToList(name, pubkey) }
    }

    override fun removeFromListBlocking(name: String, pubkey: String): Response<NostrEvent> {
        return toBlocking { removeFromList(name, pubkey) }
    }

    override fun getListBlocking(name: String): Response<NostrList> {
        return toBlocking { getList(name) }
    }

    override fun getListsBlocking(): Response<List<NostrList>> {
        return toBlocking { getLists() }
    }

    override fun getListsBlocking(pubkey: String): Response<List<NostrList>> {
        return toBlocking { getLists(pubkey) }
    }
}
