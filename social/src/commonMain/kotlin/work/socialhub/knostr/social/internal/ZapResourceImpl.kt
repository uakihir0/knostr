package work.socialhub.knostr.social.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.internal.InternalUtility
import work.socialhub.knostr.social.api.LnurlPayInfo
import work.socialhub.knostr.social.api.ZapResource
import work.socialhub.knostr.social.model.NostrZap
import work.socialhub.knostr.util.toBlocking
import work.socialhub.khttpclient.HttpRequest
import kotlin.time.Clock

class ZapResourceImpl(
    private val nostr: Nostr,
) : ZapResource {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun createZapRequest(
        recipientPubkey: String,
        amountMilliSats: Long,
        relays: List<String>,
        message: String,
        eventId: String?,
    ): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to create zap request")

        val tags = mutableListOf(
            listOf("p", recipientPubkey),
            listOf("amount", amountMilliSats.toString()),
            listOf("relays") + relays,
        )

        if (eventId != null) {
            tags.add(listOf("e", eventId))
        }

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.ZAP_REQUEST,
            tags = tags,
            content = message,
        )
        val signed = signer.sign(unsigned)
        return Response(signed)
    }

    override suspend fun getZapsForEvent(eventId: String, limit: Int): Response<List<NostrZap>> {
        val filter = NostrFilter(
            kinds = listOf(EventKind.ZAP_RECEIPT),
            eTags = listOf(eventId),
            limit = limit,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val zaps = response.data.mapNotNull { SocialMapper.toZap(it) }
        return Response(zaps)
    }

    override suspend fun getZapsForUser(pubkey: String, limit: Int): Response<List<NostrZap>> {
        val filter = NostrFilter(
            kinds = listOf(EventKind.ZAP_RECEIPT),
            pTags = listOf(pubkey),
            limit = limit,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val zaps = response.data.mapNotNull { SocialMapper.toZap(it) }
        return Response(zaps)
    }

    override suspend fun getLnurlPayInfo(lud16: String): Response<LnurlPayInfo> {
        // Parse lud16 (Lightning address): user@domain -> https://domain/.well-known/lnurlp/user
        val parts = lud16.split("@")
        if (parts.size != 2) {
            throw NostrException("Invalid lud16 format: $lud16")
        }
        val (user, domain) = parts
        val url = "https://$domain/.well-known/lnurlp/$user"

        val response = HttpRequest()
            .url(url)
            .accept("application/json")
            .get()

        val body = response.stringBody
        if (body.isBlank()) {
            throw NostrException("Failed to fetch LNURL pay info")
        }

        val info = json.decodeFromString<LnurlPayInfo>(body)
        return Response(info)
    }

    override fun createZapRequestBlocking(
        recipientPubkey: String,
        amountMilliSats: Long,
        relays: List<String>,
        message: String,
        eventId: String?,
    ): Response<NostrEvent> {
        return toBlocking { createZapRequest(recipientPubkey, amountMilliSats, relays, message, eventId) }
    }

    override fun getZapsForEventBlocking(eventId: String, limit: Int): Response<List<NostrZap>> {
        return toBlocking { getZapsForEvent(eventId, limit) }
    }

    override fun getZapsForUserBlocking(pubkey: String, limit: Int): Response<List<NostrZap>> {
        return toBlocking { getZapsForUser(pubkey, limit) }
    }

    override fun getLnurlPayInfoBlocking(lud16: String): Response<LnurlPayInfo> {
        return toBlocking { getLnurlPayInfo(lud16) }
    }
}
