package work.socialhub.knostr.social.internal

import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.internal.InternalUtility
import work.socialhub.knostr.nip44.Nip44
import work.socialhub.knostr.signing.NostrSigner
import work.socialhub.knostr.signing.createSigner
import work.socialhub.knostr.social.api.MessageResource
import work.socialhub.knostr.social.model.NostrDirectMessage
import work.socialhub.knostr.util.Hex
import work.socialhub.knostr.util.toBlocking
import kotlin.time.Clock

class MessageResourceImpl(
    private val nostr: Nostr,
) : MessageResource {

    // --- NIP-17 Gift Wrap DM ---

    override suspend fun sendMessage(recipientPubkey: String, content: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to send DM")

        val myPubkey = signer.getPublicKey()
        val now = Clock.System.now().epochSeconds

        // Step 1: Create unsigned rumor (kind:14) — NOT signed
        val rumorJson = serializeRumor(
            pubkey = myPubkey,
            createdAt = now,
            kind = EventKind.CHAT_MESSAGE,
            tags = listOf(listOf("p", recipientPubkey)),
            content = content,
        )

        // Step 2: Create Seal (kind:13) — encrypt rumor for recipient, sign with sender's key
        val sealContent = signer.nip44Encrypt(rumorJson, recipientPubkey)
        val sealUnsigned = UnsignedEvent(
            pubkey = myPubkey,
            createdAt = randomTimestamp(now),
            kind = EventKind.SEAL,
            tags = listOf(),
            content = sealContent,
        )
        val seal = signer.sign(sealUnsigned)

        // Step 3: Create Gift Wrap (kind:1059) with ephemeral key for recipient
        val giftWrapForRecipient = createGiftWrap(seal, recipientPubkey, now)
        nostr.events().publishEvent(giftWrapForRecipient)

        // Step 4: Create Gift Wrap for self (archive copy)
        val giftWrapForSelf = createGiftWrap(seal, myPubkey, now)
        nostr.events().publishEvent(giftWrapForSelf)

        return Response(giftWrapForRecipient)
    }

    override suspend fun getMessages(since: Long?, until: Long?, limit: Int): Response<List<NostrDirectMessage>> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to get DMs")

        val myPubkey = signer.getPublicKey()
        val filter = NostrFilter(
            kinds = listOf(EventKind.GIFT_WRAP),
            pTags = listOf(myPubkey),
            since = since,
            until = until,
            limit = limit,
        )

        val response = nostr.events().queryEvents(listOf(filter))
        val messages = response.data.mapNotNull { giftWrap ->
            unwrapGiftWrap(giftWrap, signer)
        }.sortedByDescending { it.createdAt }

        return Response(messages)
    }

    override suspend fun getConversation(pubkey: String, since: Long?, until: Long?, limit: Int): Response<List<NostrDirectMessage>> {
        val response = getMessages(since, until, limit)
        val filtered = response.data.filter {
            it.senderPubkey == pubkey || it.recipientPubkey == pubkey
        }
        return Response(filtered)
    }

    // --- Internal NIP-17 helpers ---

    /**
     * Create a Gift Wrap (kind:1059) using an ephemeral key.
     * The gift wrap encrypts the seal for the target recipient.
     */
    private fun createGiftWrap(seal: NostrEvent, recipientPubkey: String, baseTime: Long): NostrEvent {
        // Generate ephemeral keypair
        val ephemeralPrivKeyBytes = randomBytes(32)
        val ephemeralPrivKeyHex = Hex.encode(ephemeralPrivKeyBytes)
        val ephemeralSigner = createSigner(ephemeralPrivKeyHex)

        // Encrypt sealed event for recipient using ephemeral key
        val sealJson = InternalUtility.toJson(seal)
        val encryptedSeal = ephemeralSigner.nip44Encrypt(sealJson, recipientPubkey)

        val giftWrapUnsigned = UnsignedEvent(
            pubkey = ephemeralSigner.getPublicKey(),
            createdAt = randomTimestamp(baseTime),
            kind = EventKind.GIFT_WRAP,
            tags = listOf(listOf("p", recipientPubkey)),
            content = encryptedSeal,
        )
        return ephemeralSigner.sign(giftWrapUnsigned)
    }

    /**
     * Unwrap a Gift Wrap event to extract the DM content.
     * Returns null if unwrapping fails (e.g., not addressed to us, invalid format).
     */
    private fun unwrapGiftWrap(giftWrap: NostrEvent, signer: NostrSigner): NostrDirectMessage? {
        return try {
            // Decrypt outer layer: Gift Wrap → Seal
            val sealJson = signer.nip44Decrypt(giftWrap.content, giftWrap.pubkey)
            val seal = InternalUtility.fromJson<NostrEvent>(sealJson)

            if (seal.kind != EventKind.SEAL) return null

            // Decrypt inner layer: Seal → Rumor
            val rumorJson = signer.nip44Decrypt(seal.content, seal.pubkey)
            val rumor = InternalUtility.fromJson<RumorEvent>(rumorJson)

            if (rumor.kind != EventKind.CHAT_MESSAGE) return null

            // Extract recipient from p-tag
            val recipientPubkey = rumor.tags
                .firstOrNull { it.size >= 2 && it[0] == "p" }
                ?.get(1) ?: return null

            NostrDirectMessage(
                id = giftWrap.id,
                senderPubkey = seal.pubkey,
                recipientPubkey = recipientPubkey,
                content = rumor.content,
                createdAt = rumor.createdAt,
                event = giftWrap,
                isLegacy = false,
            )
        } catch (_: Exception) {
            null // Failed to unwrap — skip
        }
    }

    /**
     * Serialize a rumor event as JSON (no id, no sig — unsigned event).
     * Format matches what NIP-17 expects inside a Seal.
     */
    private fun serializeRumor(
        pubkey: String,
        createdAt: Long,
        kind: Int,
        tags: List<List<String>>,
        content: String,
    ): String {
        val rumor = RumorEvent(
            pubkey = pubkey,
            createdAt = createdAt,
            kind = kind,
            tags = tags,
            content = content,
        )
        return InternalUtility.toJson(rumor)
    }

    /**
     * Generate a randomized timestamp within ±2 days for metadata privacy.
     */
    private fun randomTimestamp(baseTime: Long): Long {
        val twoDays = 2 * 24 * 60 * 60L
        val offset = kotlin.random.Random.nextLong(-twoDays, twoDays)
        return baseTime + offset
    }

    /**
     * Generate random bytes for ephemeral key generation.
     */
    private fun randomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        val random = kotlin.random.Random
        for (i in bytes.indices) {
            bytes[i] = random.nextInt(256).toByte()
        }
        return bytes
    }

    // --- NIP-04 Legacy DM ---

    override suspend fun sendLegacyMessage(recipientPubkey: String, content: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to send DM")

        val myPubkey = signer.getPublicKey()
        val now = Clock.System.now().epochSeconds

        val encrypted = signer.nip04Encrypt(content, recipientPubkey)

        val unsigned = UnsignedEvent(
            pubkey = myPubkey,
            createdAt = now,
            kind = EventKind.ENCRYPTED_DM,
            tags = listOf(listOf("p", recipientPubkey)),
            content = encrypted,
        )
        val event = signer.sign(unsigned)
        nostr.events().publishEvent(event)

        return Response(event)
    }

    override suspend fun getLegacyMessages(since: Long?, until: Long?, limit: Int): Response<List<NostrDirectMessage>> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to get DMs")

        val myPubkey = signer.getPublicKey()

        // Query kind:4 events addressed to us and sent by us
        val receivedFilter = NostrFilter(
            kinds = listOf(EventKind.ENCRYPTED_DM),
            pTags = listOf(myPubkey),
            since = since,
            until = until,
            limit = limit,
        )
        val sentFilter = NostrFilter(
            kinds = listOf(EventKind.ENCRYPTED_DM),
            authors = listOf(myPubkey),
            since = since,
            until = until,
            limit = limit,
        )

        val response = nostr.events().queryEvents(listOf(receivedFilter, sentFilter))
        val messages = response.data.mapNotNull { event ->
            decryptLegacyDm(event, signer, myPubkey)
        }.sortedByDescending { it.createdAt }

        return Response(messages)
    }

    // --- Internal NIP-04 helpers ---

    private fun decryptLegacyDm(event: NostrEvent, signer: NostrSigner, myPubkey: String): NostrDirectMessage? {
        return try {
            if (event.kind != EventKind.ENCRYPTED_DM) return null

            val recipientPubkey = event.tags
                .firstOrNull { it.size >= 2 && it[0] == "p" }
                ?.get(1) ?: return null

            // Determine the other party's pubkey for decryption
            val otherPubkey = if (event.pubkey == myPubkey) recipientPubkey else event.pubkey
            val decrypted = signer.nip04Decrypt(event.content, otherPubkey)

            NostrDirectMessage(
                id = event.id,
                senderPubkey = event.pubkey,
                recipientPubkey = recipientPubkey,
                content = decrypted,
                createdAt = event.createdAt,
                event = event,
                isLegacy = true,
            )
        } catch (_: Exception) {
            null
        }
    }

    // --- Blocking variants ---

    override fun sendMessageBlocking(recipientPubkey: String, content: String): Response<NostrEvent> {
        return toBlocking { sendMessage(recipientPubkey, content) }
    }

    override fun getMessagesBlocking(since: Long?, until: Long?, limit: Int): Response<List<NostrDirectMessage>> {
        return toBlocking { getMessages(since, until, limit) }
    }

    override fun getConversationBlocking(pubkey: String, since: Long?, until: Long?, limit: Int): Response<List<NostrDirectMessage>> {
        return toBlocking { getConversation(pubkey, since, until, limit) }
    }

    override fun sendLegacyMessageBlocking(recipientPubkey: String, content: String): Response<NostrEvent> {
        return toBlocking { sendLegacyMessage(recipientPubkey, content) }
    }

    override fun getLegacyMessagesBlocking(since: Long?, until: Long?, limit: Int): Response<List<NostrDirectMessage>> {
        return toBlocking { getLegacyMessages(since, until, limit) }
    }
}

/**
 * Internal rumor event model (unsigned event inside a Seal).
 * Unlike NostrEvent, this has no id or sig fields.
 */
@kotlinx.serialization.Serializable
internal data class RumorEvent(
    val pubkey: String,
    @kotlinx.serialization.SerialName("created_at")
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>> = listOf(),
    val content: String,
)
