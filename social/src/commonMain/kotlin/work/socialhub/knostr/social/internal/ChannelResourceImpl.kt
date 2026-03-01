package work.socialhub.knostr.social.internal

import kotlinx.serialization.Serializable
import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.internal.InternalUtility
import work.socialhub.knostr.signing.NostrSigner
import work.socialhub.knostr.social.api.ChannelResource
import work.socialhub.knostr.social.model.NostrChannel
import work.socialhub.knostr.social.model.NostrChannelMessage
import work.socialhub.knostr.util.toBlocking
import kotlin.time.Clock

class ChannelResourceImpl(
    private val nostr: Nostr,
) : ChannelResource {

    @Serializable
    private data class ChannelMetadata(
        val name: String = "",
        val about: String = "",
        val picture: String = "",
    )

    override suspend fun createChannel(name: String, about: String, picture: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to create channel")

        val metadata = ChannelMetadata(name, about, picture)
        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.CHANNEL_CREATE,
            tags = listOf(),
            content = InternalUtility.toJson(metadata),
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun updateChannel(channelId: String, name: String?, about: String?, picture: String?): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to update channel")

        // Get current channel metadata
        val current = getChannel(channelId).data
        val metadata = ChannelMetadata(
            name = name ?: current.name,
            about = about ?: current.about,
            picture = picture ?: current.picture,
        )

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.CHANNEL_METADATA,
            tags = listOf(listOf("e", channelId)),
            content = InternalUtility.toJson(metadata),
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun sendMessage(channelId: String, content: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to send channel message")

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.CHANNEL_MESSAGE,
            tags = listOf(listOf("e", channelId, "", "root")),
            content = content,
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun getChannelMessages(channelId: String, since: Long?, until: Long?, limit: Int): Response<List<NostrChannelMessage>> {
        val filter = NostrFilter(
            kinds = listOf(EventKind.CHANNEL_MESSAGE),
            eTags = listOf(channelId),
            since = since,
            until = until,
            limit = limit,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val messages = response.data.map { event ->
            NostrChannelMessage().apply {
                this.event = event
                this.content = event.content
                this.channelId = channelId
                this.createdAt = event.createdAt
            }
        }.sortedBy { it.createdAt }
        return Response(messages)
    }

    override suspend fun getChannel(channelId: String): Response<NostrChannel> {
        // Get the creation event
        val createFilter = NostrFilter(
            ids = listOf(channelId),
            kinds = listOf(EventKind.CHANNEL_CREATE),
            limit = 1,
        )
        val createResponse = nostr.events().queryEvents(listOf(createFilter))
        val createEvent = createResponse.data.firstOrNull()
            ?: throw NostrException("Channel not found: $channelId")

        val channel = parseChannelFromEvent(createEvent)

        // Check for metadata updates
        val metaFilter = NostrFilter(
            kinds = listOf(EventKind.CHANNEL_METADATA),
            eTags = listOf(channelId),
            limit = 1,
        )
        val metaResponse = nostr.events().queryEvents(listOf(metaFilter))
        val metaEvent = metaResponse.data
            .filter { it.pubkey == createEvent.pubkey }
            .maxByOrNull { it.createdAt }

        if (metaEvent != null) {
            val updated = parseChannelMetadata(metaEvent.content)
            if (updated != null) {
                channel.name = updated.name
                channel.about = updated.about
                channel.picture = updated.picture
            }
        }

        return Response(channel)
    }

    override suspend fun getChannels(limit: Int): Response<List<NostrChannel>> {
        val filter = NostrFilter(
            kinds = listOf(EventKind.CHANNEL_CREATE),
            limit = limit,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val channels = response.data.map { parseChannelFromEvent(it) }
        return Response(channels)
    }

    private fun parseChannelFromEvent(event: NostrEvent): NostrChannel {
        val channel = NostrChannel()
        channel.id = event.id
        channel.createdAt = event.createdAt

        val metadata = parseChannelMetadata(event.content)
        if (metadata != null) {
            channel.name = metadata.name
            channel.about = metadata.about
            channel.picture = metadata.picture
        }

        return channel
    }

    private fun parseChannelMetadata(content: String): ChannelMetadata? {
        return try {
            InternalUtility.fromJson<ChannelMetadata>(content)
        } catch (_: Exception) {
            null
        }
    }

    override fun createChannelBlocking(name: String, about: String, picture: String): Response<NostrEvent> {
        return toBlocking { createChannel(name, about, picture) }
    }

    override fun updateChannelBlocking(channelId: String, name: String?, about: String?, picture: String?): Response<NostrEvent> {
        return toBlocking { updateChannel(channelId, name, about, picture) }
    }

    override fun sendMessageBlocking(channelId: String, content: String): Response<NostrEvent> {
        return toBlocking { sendMessage(channelId, content) }
    }

    override fun getChannelMessagesBlocking(channelId: String, since: Long?, until: Long?, limit: Int): Response<List<NostrChannelMessage>> {
        return toBlocking { getChannelMessages(channelId, since, until, limit) }
    }

    override fun getChannelBlocking(channelId: String): Response<NostrChannel> {
        return toBlocking { getChannel(channelId) }
    }

    override fun getChannelsBlocking(limit: Int): Response<List<NostrChannel>> {
        return toBlocking { getChannels(limit) }
    }

    // NIP-51 Public Chats List (kind:10005)

    override suspend fun getJoinedChannels(): Response<List<String>> {
        val tags = getPublicChatsListTags()
        val channelIds = tags
            .filter { it.size >= 2 && it[0] == "e" }
            .map { it[1] }
        return Response(channelIds)
    }

    override suspend fun joinChannel(channelId: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to join channel")

        val currentTags = getPublicChatsListTags()
        val tags = currentTags.toMutableList()
        if (tags.none { it.size >= 2 && it[0] == "e" && it[1] == channelId }) {
            tags.add(listOf("e", channelId))
        }

        return publishPublicChatsList(signer, tags)
    }

    override suspend fun leaveChannel(channelId: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to leave channel")

        val currentTags = getPublicChatsListTags()
        val tags = currentTags.filter { !(it.size >= 2 && it[0] == "e" && it[1] == channelId) }

        return publishPublicChatsList(signer, tags)
    }

    private suspend fun getPublicChatsListTags(): List<List<String>> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to get public chats list")

        val filter = NostrFilter(
            authors = listOf(signer.getPublicKey()),
            kinds = listOf(EventKind.PUBLIC_CHATS_LIST),
            limit = 1,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        return response.data.firstOrNull()?.tags ?: listOf()
    }

    private suspend fun publishPublicChatsList(
        signer: NostrSigner,
        tags: List<List<String>>,
    ): Response<NostrEvent> {
        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.PUBLIC_CHATS_LIST,
            tags = tags,
            content = "",
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override fun getJoinedChannelsBlocking(): Response<List<String>> {
        return toBlocking { getJoinedChannels() }
    }

    override fun joinChannelBlocking(channelId: String): Response<NostrEvent> {
        return toBlocking { joinChannel(channelId) }
    }

    override fun leaveChannelBlocking(channelId: String): Response<NostrEvent> {
        return toBlocking { leaveChannel(channelId) }
    }
}
