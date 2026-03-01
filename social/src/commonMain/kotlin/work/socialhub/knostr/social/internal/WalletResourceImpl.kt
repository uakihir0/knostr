package work.socialhub.knostr.social.internal

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import work.socialhub.knostr.EventKind
import work.socialhub.knostr.NostrConfig
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.nip44.Nip44
import work.socialhub.knostr.relay.RelayPool
import work.socialhub.knostr.signing.Secp256k1Signer
import work.socialhub.knostr.social.api.WalletResource
import work.socialhub.knostr.social.model.NwcTransaction
import work.socialhub.knostr.util.Hex
import work.socialhub.knostr.util.toBlocking
import kotlin.random.Random
import kotlin.time.Clock

class WalletResourceImpl : WalletResource {

    private var walletPubkey: String? = null
    private var clientSigner: Secp256k1Signer? = null
    private var conversationKey: ByteArray? = null
    private var relayPool: RelayPool? = null
    private var subscriptionId: String? = null
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<NwcResult>>()
    private var requestTimeoutMs: Long = 60_000

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Serializable
    private data class NwcRequest(
        val method: String,
        val params: JsonObject = JsonObject(emptyMap()),
    )

    private data class NwcResult(
        val resultType: String,
        val result: JsonElement?,
        val error: NwcError?,
    )

    private data class NwcError(
        val code: String,
        val message: String,
    )

    override suspend fun connect(nwcUrl: String) {
        val parts = parseNwcUrl(nwcUrl)

        val signer = Secp256k1Signer(parts.secret)
        val convKey = Nip44.deriveConversationKey(parts.secret, parts.pubkey)

        val pool = RelayPool()
        for (relay in parts.relays) {
            pool.addRelay(relay)
        }
        pool.connectAll(CoroutineScope(Dispatchers.Default))

        this.walletPubkey = parts.pubkey
        this.clientSigner = signer
        this.conversationKey = convKey
        this.relayPool = pool

        val filter = NostrFilter(
            kinds = listOf(EventKind.NWC_RESPONSE),
            authors = listOf(parts.pubkey),
            pTags = listOf(signer.getPublicKey()),
        )
        subscriptionId = pool.subscribe(
            filters = listOf(filter),
            onEvent = { event -> handleResponse(event) },
        )
    }

    private fun handleResponse(event: NostrEvent) {
        val convKey = conversationKey ?: return
        if (event.pubkey != walletPubkey) return

        try {
            val decrypted = Nip44.decrypt(event.content, convKey)
            val responseJson = json.parseToJsonElement(decrypted).jsonObject

            val resultType = responseJson["result_type"]?.jsonPrimitive?.content ?: ""
            val result = responseJson["result"]
            val errorObj = responseJson["error"]?.jsonObject
            val error = if (errorObj != null) {
                NwcError(
                    code = errorObj["code"]?.jsonPrimitive?.content ?: "",
                    message = errorObj["message"]?.jsonPrimitive?.content ?: "",
                )
            } else null

            // Match response to request via e-tag
            val eTag = event.tags.firstOrNull { it.size >= 2 && it[0] == "e" }?.get(1)
            if (eTag != null) {
                pendingRequests[eTag]?.complete(NwcResult(resultType, result, error))
            }
        } catch (_: Exception) {
            // Ignore malformed responses
        }
    }

    private suspend fun sendRequest(method: String, params: JsonObject = JsonObject(emptyMap())): JsonElement? {
        val signer = clientSigner
            ?: throw NostrException("Not connected to wallet. Call connect() first.")
        val pool = relayPool
            ?: throw NostrException("Not connected to wallet. Call connect() first.")
        val walletPk = walletPubkey
            ?: throw NostrException("Not connected to wallet. Call connect() first.")
        val convKey = conversationKey
            ?: throw NostrException("Not connected to wallet. Call connect() first.")

        val request = NwcRequest(method = method, params = params)
        val requestJson = json.encodeToString(request)
        val encrypted = Nip44.encrypt(requestJson, convKey)

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.NWC_REQUEST,
            tags = listOf(listOf("p", walletPk)),
            content = encrypted,
        )
        val signed = signer.sign(unsigned)

        val deferred = CompletableDeferred<NwcResult>()
        pendingRequests[signed.id] = deferred

        pool.publishEvent(signed)

        val result = try {
            withTimeout(requestTimeoutMs) {
                deferred.await()
            }
        } finally {
            pendingRequests.remove(signed.id)
        }

        if (result.error != null) {
            throw NostrException("NWC error [${result.error.code}]: ${result.error.message}")
        }

        return result.result
    }

    override suspend fun payInvoice(invoice: String): Response<String> {
        val params = buildJsonObject {
            put("invoice", JsonPrimitive(invoice))
        }
        val result = sendRequest("pay_invoice", params)
        val preimage = result?.jsonObject?.get("preimage")?.jsonPrimitive?.content ?: ""
        return Response(preimage)
    }

    override suspend fun makeInvoice(amountMsats: Long, description: String): Response<String> {
        val params = buildJsonObject {
            put("amount", JsonPrimitive(amountMsats))
            if (description.isNotEmpty()) put("description", JsonPrimitive(description))
        }
        val result = sendRequest("make_invoice", params)
        val invoice = result?.jsonObject?.get("invoice")?.jsonPrimitive?.content ?: ""
        return Response(invoice)
    }

    override suspend fun getBalance(): Response<Long> {
        val result = sendRequest("get_balance")
        val balance = result?.jsonObject?.get("balance")?.jsonPrimitive?.long ?: 0
        return Response(balance)
    }

    override suspend fun getInfo(): Response<List<String>> {
        val result = sendRequest("get_info")
        val methods = result?.jsonObject?.get("methods")?.jsonArray
            ?.map { it.jsonPrimitive.content }
            ?: listOf()
        return Response(methods)
    }

    override suspend fun listTransactions(since: Long?, until: Long?, limit: Int): Response<List<NwcTransaction>> {
        val params = buildJsonObject {
            if (since != null) put("from", JsonPrimitive(since))
            if (until != null) put("until", JsonPrimitive(until))
            put("limit", JsonPrimitive(limit))
        }
        val result = sendRequest("list_transactions", params)
        val transactions = result?.jsonObject?.get("transactions")?.jsonArray
            ?.map { json.decodeFromJsonElement(NwcTransaction.serializer(), it) }
            ?: listOf()
        return Response(transactions)
    }

    override suspend fun disconnect() {
        subscriptionId?.let { relayPool?.unsubscribe(it) }
        relayPool?.disconnectAll()
        pendingRequests.values.forEach {
            it.completeExceptionally(NostrException("Wallet disconnected"))
        }
        pendingRequests.clear()
        walletPubkey = null
        clientSigner = null
        conversationKey = null
        relayPool = null
        subscriptionId = null
    }

    override fun connectBlocking(nwcUrl: String) {
        toBlocking { connect(nwcUrl) }
    }

    override fun payInvoiceBlocking(invoice: String): Response<String> {
        return toBlocking { payInvoice(invoice) }
    }

    override fun makeInvoiceBlocking(amountMsats: Long, description: String): Response<String> {
        return toBlocking { makeInvoice(amountMsats, description) }
    }

    override fun getBalanceBlocking(): Response<Long> {
        return toBlocking { getBalance() }
    }

    override fun getInfoBlocking(): Response<List<String>> {
        return toBlocking { getInfo() }
    }

    override fun listTransactionsBlocking(since: Long?, until: Long?, limit: Int): Response<List<NwcTransaction>> {
        return toBlocking { listTransactions(since, until, limit) }
    }

    override fun disconnectBlocking() {
        toBlocking { disconnect() }
    }

    companion object {
        data class NwcUrlParts(
            val pubkey: String,
            val relays: List<String>,
            val secret: String,
        )

        fun parseNwcUrl(url: String): NwcUrlParts {
            val normalized = url.replace("nostr+walletconnect://", "")
            val qIdx = normalized.indexOf('?')
            val pubkey = if (qIdx >= 0) normalized.substring(0, qIdx) else normalized

            val params = if (qIdx >= 0) {
                normalized.substring(qIdx + 1).split("&").associate {
                    val parts = it.split("=", limit = 2)
                    parts[0] to (if (parts.size > 1) parts[1] else "")
                }
            } else {
                emptyMap()
            }

            val relays = params.entries
                .filter { it.key == "relay" }
                .map { it.value }
                .ifEmpty {
                    params["relay"]?.let { listOf(it) } ?: listOf()
                }

            val secret = params["secret"]
                ?: throw NostrException("NWC URL must include a secret parameter")

            return NwcUrlParts(pubkey = pubkey, relays = relays, secret = secret)
        }
    }
}
