package work.socialhub.knostr.signing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import work.socialhub.knostr.EventKind
import work.socialhub.knostr.NostrConfig
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.internal.InternalUtility
import work.socialhub.knostr.nip44.Nip44
import work.socialhub.knostr.relay.RelayPool
import work.socialhub.knostr.util.Hex
import kotlin.random.Random
import kotlin.time.Clock

/**
 * NIP-46 Nostr Connect (Bunker) signer.
 *
 * Proxies signing operations through a remote signer via relay-mediated
 * NIP-44 encrypted JSON-RPC messages (kind:24133 request / kind:24134 response).
 *
 * Usage:
 * ```
 * val signer = BunkerSigner.create("bunker://pubkey?relay=wss://relay.example.com&secret=xxx")
 * val nostr = NostrFactory.instance(NostrConfig().also {
 *     it.signer = signer
 *     it.relayUrls = listOf("wss://relay.example.com")
 * })
 * ```
 */
class BunkerSigner private constructor(
    private val remotePubkey: String,
    private val clientPrivateKeyHex: String,
    private val clientSigner: Secp256k1Signer,
    private val relayPool: RelayPool,
    private val requestTimeoutMs: Long,
) : NostrSigner {

    private val conversationKey = Nip44.deriveConversationKey(clientPrivateKeyHex, remotePubkey)
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<BunkerResponse>>()
    private var subscriptionId: String? = null

    @Serializable
    private data class BunkerRequest(
        val id: String,
        val method: String,
        val params: List<String> = listOf(),
    )

    @Serializable
    private data class BunkerResponse(
        val id: String,
        val result: String = "",
        val error: String = "",
    )

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        /**
         * Parse a bunker:// URL into its components.
         *
         * Format: bunker://remote-pubkey?relay=wss://...&secret=...
         */
        fun parseBunkerUrl(url: String): BunkerUrlParts {
            require(url.startsWith("bunker://")) { "Invalid bunker URL: must start with bunker://" }

            val withoutScheme = url.removePrefix("bunker://")
            val qIdx = withoutScheme.indexOf('?')
            val pubkey = if (qIdx >= 0) withoutScheme.substring(0, qIdx) else withoutScheme
            val params = if (qIdx >= 0) {
                withoutScheme.substring(qIdx + 1).split("&").associate {
                    val (k, v) = it.split("=", limit = 2)
                    k to v
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

            return BunkerUrlParts(
                pubkey = pubkey,
                relays = relays,
                secret = params["secret"],
            )
        }

        /**
         * Generate a random private key (32 bytes hex).
         */
        private fun generatePrivateKey(): String {
            val bytes = ByteArray(32)
            val random = Random
            for (i in bytes.indices) {
                bytes[i] = random.nextInt(256).toByte()
            }
            return Hex.encode(bytes)
        }

        /**
         * Create a BunkerSigner from a bunker:// URL.
         *
         * This connects to the relay, sends a "connect" request, and waits for acknowledgement.
         *
         * @param bunkerUrl bunker://pubkey?relay=wss://...&secret=...
         * @param clientPrivateKeyHex optional client private key (generated if null)
         * @param requestTimeoutMs timeout for each RPC request (default 60s)
         */
        suspend fun create(
            bunkerUrl: String,
            clientPrivateKeyHex: String? = null,
            requestTimeoutMs: Long = 60_000,
        ): BunkerSigner {
            val parts = parseBunkerUrl(bunkerUrl)
            require(parts.relays.isNotEmpty()) { "Bunker URL must include at least one relay" }

            val privKey = clientPrivateKeyHex ?: generatePrivateKey()
            val clientSigner = Secp256k1Signer(privKey)

            val pool = RelayPool()
            for (relay in parts.relays) {
                pool.addRelay(relay)
            }
            pool.connectAll(CoroutineScope(Dispatchers.Default))

            val signer = BunkerSigner(
                remotePubkey = parts.pubkey,
                clientPrivateKeyHex = privKey,
                clientSigner = clientSigner,
                relayPool = pool,
                requestTimeoutMs = requestTimeoutMs,
            )

            signer.startListening()

            // Send connect request
            val connectParams = mutableListOf(parts.pubkey)
            if (parts.secret != null) {
                connectParams.add(parts.secret)
            }
            signer.sendRequest("connect", connectParams)

            return signer
        }
    }

    data class BunkerUrlParts(
        val pubkey: String,
        val relays: List<String>,
        val secret: String?,
    )

    private suspend fun startListening() {
        val filter = NostrFilter(
            kinds = listOf(EventKind.NIP46_RESPONSE),
            pTags = listOf(clientSigner.getPublicKey()),
        )
        subscriptionId = relayPool.subscribe(
            filters = listOf(filter),
            onEvent = { event -> handleResponse(event) },
        )
    }

    private fun handleResponse(event: NostrEvent) {
        if (event.pubkey != remotePubkey) return

        try {
            val decrypted = Nip44.decrypt(event.content, conversationKey)
            val response = json.decodeFromString<BunkerResponse>(decrypted)
            pendingRequests[response.id]?.complete(response)
        } catch (_: Exception) {
            // Ignore malformed responses
        }
    }

    private suspend fun sendRequest(method: String, params: List<String> = listOf()): String {
        val requestId = generateRequestId()
        val request = BunkerRequest(id = requestId, method = method, params = params)
        val requestJson = json.encodeToString(request)
        val encrypted = Nip44.encrypt(requestJson, conversationKey)

        val unsigned = UnsignedEvent(
            pubkey = clientSigner.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.NIP46_REQUEST,
            tags = listOf(listOf("p", remotePubkey)),
            content = encrypted,
        )
        val signed = clientSigner.sign(unsigned)

        val deferred = CompletableDeferred<BunkerResponse>()
        pendingRequests[requestId] = deferred

        relayPool.publishEvent(signed)

        val response = try {
            withTimeout(requestTimeoutMs) {
                deferred.await()
            }
        } finally {
            pendingRequests.remove(requestId)
        }

        if (response.error.isNotEmpty()) {
            throw NostrException("Bunker error: ${response.error}")
        }

        return response.result
    }

    private fun generateRequestId(): String {
        val bytes = ByteArray(16)
        val random = Random
        for (i in bytes.indices) {
            bytes[i] = random.nextInt(256).toByte()
        }
        return Hex.encode(bytes)
    }

    override fun getPublicKey(): String = remotePubkey

    override fun sign(event: UnsignedEvent): NostrEvent {
        throw UnsupportedOperationException(
            "BunkerSigner.sign is async. Use signAsync() suspend function instead."
        )
    }

    /**
     * Sign an event asynchronously via the remote bunker.
     */
    suspend fun signAsync(event: UnsignedEvent): NostrEvent {
        val eventJson = buildJsonArray {
            add(JsonPrimitive(0))
            add(JsonPrimitive(event.pubkey.ifEmpty { remotePubkey }))
            add(JsonPrimitive(event.createdAt))
            add(JsonPrimitive(event.kind))
            add(buildJsonArray {
                for (tag in event.tags) {
                    add(buildJsonArray { tag.forEach { add(JsonPrimitive(it)) } })
                }
            })
            add(JsonPrimitive(event.content))
        }.toString()

        // Send unsigned event JSON for signing
        val unsignedJson = json.encodeToString(
            mapOf(
                "pubkey" to (event.pubkey.ifEmpty { remotePubkey }),
                "created_at" to event.createdAt.toString(),
                "kind" to event.kind.toString(),
                "tags" to InternalUtility.json.encodeToString(JsonArray.serializer(), buildJsonArray {
                    for (tag in event.tags) {
                        add(buildJsonArray { tag.forEach { add(JsonPrimitive(it)) } })
                    }
                }),
                "content" to event.content,
            )
        )

        val resultJson = sendRequest("sign_event", listOf(unsignedJson))
        return InternalUtility.fromJson<NostrEvent>(resultJson)
    }

    override fun computeEventId(event: UnsignedEvent): String {
        // Can compute locally since it's just SHA-256
        return clientSigner.computeEventId(
            event.copy(pubkey = event.pubkey.ifEmpty { remotePubkey })
        )
    }

    override fun nip44Encrypt(plaintext: String, recipientPubkey: String): String {
        throw UnsupportedOperationException(
            "BunkerSigner.nip44Encrypt is async. Use nip44EncryptAsync() suspend function instead."
        )
    }

    override fun nip44Decrypt(payload: String, senderPubkey: String): String {
        throw UnsupportedOperationException(
            "BunkerSigner.nip44Decrypt is async. Use nip44DecryptAsync() suspend function instead."
        )
    }

    override fun nip04Encrypt(plaintext: String, recipientPubkey: String): String {
        throw UnsupportedOperationException(
            "BunkerSigner.nip04Encrypt is async. Use nip04EncryptAsync() suspend function instead."
        )
    }

    override fun nip04Decrypt(ciphertext: String, senderPubkey: String): String {
        throw UnsupportedOperationException(
            "BunkerSigner.nip04Decrypt is async. Use nip04DecryptAsync() suspend function instead."
        )
    }

    /** Encrypt using NIP-44 via the remote bunker */
    suspend fun nip44EncryptAsync(plaintext: String, recipientPubkey: String): String {
        return sendRequest("nip44_encrypt", listOf(recipientPubkey, plaintext))
    }

    /** Decrypt using NIP-44 via the remote bunker */
    suspend fun nip44DecryptAsync(payload: String, senderPubkey: String): String {
        return sendRequest("nip44_decrypt", listOf(senderPubkey, payload))
    }

    /** Encrypt using NIP-04 via the remote bunker */
    suspend fun nip04EncryptAsync(plaintext: String, recipientPubkey: String): String {
        return sendRequest("nip04_encrypt", listOf(recipientPubkey, plaintext))
    }

    /** Decrypt using NIP-04 via the remote bunker */
    suspend fun nip04DecryptAsync(ciphertext: String, senderPubkey: String): String {
        return sendRequest("nip04_decrypt", listOf(senderPubkey, ciphertext))
    }

    /** Send a ping to the bunker */
    suspend fun ping(): String {
        return sendRequest("ping")
    }

    /** Get the client public key (used as the client identity) */
    fun getClientPublicKey(): String = clientSigner.getPublicKey()

    /** Disconnect from relays and clean up */
    suspend fun close() {
        subscriptionId?.let { relayPool.unsubscribe(it) }
        relayPool.disconnectAll()
        pendingRequests.values.forEach {
            it.completeExceptionally(NostrException("BunkerSigner closed"))
        }
        pendingRequests.clear()
    }
}
