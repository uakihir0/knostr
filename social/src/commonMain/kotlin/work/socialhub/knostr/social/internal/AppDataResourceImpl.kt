package work.socialhub.knostr.social.internal

import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.social.api.AppDataResource
import work.socialhub.knostr.util.toBlocking
import kotlin.time.Clock

class AppDataResourceImpl(
    private val nostr: Nostr,
) : AppDataResource {

    override suspend fun setAppData(dTag: String, content: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to set app data")

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.APP_SPECIFIC_DATA,
            tags = listOf(listOf("d", dTag)),
            content = content,
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun getAppData(dTag: String): Response<String?> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to get own app data")
        return getAppDataByPubkey(signer.getPublicKey(), dTag)
    }

    override suspend fun getAppDataByPubkey(pubkey: String, dTag: String): Response<String?> {
        val filter = NostrFilter(
            authors = listOf(pubkey),
            kinds = listOf(EventKind.APP_SPECIFIC_DATA),
            dTags = listOf(dTag),
            limit = 1,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val content = response.data
            .maxByOrNull { it.createdAt }
            ?.content
        return Response(content)
    }

    override fun setAppDataBlocking(dTag: String, content: String): Response<NostrEvent> {
        return toBlocking { setAppData(dTag, content) }
    }

    override fun getAppDataBlocking(dTag: String): Response<String?> {
        return toBlocking { getAppData(dTag) }
    }

    override fun getAppDataByPubkeyBlocking(pubkey: String, dTag: String): Response<String?> {
        return toBlocking { getAppDataByPubkey(pubkey, dTag) }
    }
}
