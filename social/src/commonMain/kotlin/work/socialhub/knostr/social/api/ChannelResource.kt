package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.social.model.NostrChannel
import work.socialhub.knostr.social.model.NostrChannelMessage
import kotlin.js.JsExport

@JsExport
interface ChannelResource {

    /** Create a channel (NIP-28 kind:40) */
    suspend fun createChannel(name: String, about: String = "", picture: String = ""): Response<NostrEvent>

    /** Update channel metadata (kind:41) */
    suspend fun updateChannel(channelId: String, name: String? = null, about: String? = null, picture: String? = null): Response<NostrEvent>

    /** Send a message to a channel (kind:42) */
    suspend fun sendMessage(channelId: String, content: String): Response<NostrEvent>

    /** Get channel messages */
    suspend fun getChannelMessages(channelId: String, since: Long? = null, until: Long? = null, limit: Int = 50): Response<List<NostrChannelMessage>>

    /** Get channel info */
    suspend fun getChannel(channelId: String): Response<NostrChannel>

    /** Get list of channels */
    suspend fun getChannels(limit: Int = 50): Response<List<NostrChannel>>

    /** Get user's joined channels list (NIP-51, kind:10005) */
    suspend fun getJoinedChannels(): Response<List<String>>

    /** Join a channel (add to kind:10005 list) */
    suspend fun joinChannel(channelId: String): Response<NostrEvent>

    /** Leave a channel (remove from kind:10005 list) */
    suspend fun leaveChannel(channelId: String): Response<NostrEvent>

    @JsExport.Ignore
    fun createChannelBlocking(name: String, about: String = "", picture: String = ""): Response<NostrEvent>

    @JsExport.Ignore
    fun updateChannelBlocking(channelId: String, name: String? = null, about: String? = null, picture: String? = null): Response<NostrEvent>

    @JsExport.Ignore
    fun sendMessageBlocking(channelId: String, content: String): Response<NostrEvent>

    @JsExport.Ignore
    fun getChannelMessagesBlocking(channelId: String, since: Long? = null, until: Long? = null, limit: Int = 50): Response<List<NostrChannelMessage>>

    @JsExport.Ignore
    fun getChannelBlocking(channelId: String): Response<NostrChannel>

    @JsExport.Ignore
    fun getChannelsBlocking(limit: Int = 50): Response<List<NostrChannel>>

    @JsExport.Ignore
    fun getJoinedChannelsBlocking(): Response<List<String>>

    @JsExport.Ignore
    fun joinChannelBlocking(channelId: String): Response<NostrEvent>

    @JsExport.Ignore
    fun leaveChannelBlocking(channelId: String): Response<NostrEvent>
}
