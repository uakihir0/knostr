package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.social.model.NostrFileMetadata
import work.socialhub.knostr.social.model.NostrMedia
import work.socialhub.knostr.social.model.NostrMediaPost
import work.socialhub.knostr.social.model.NostrMediaUpload
import kotlin.js.JsExport

@JsExport
interface MediaResource {

    /**
     * Upload a file to the media server configured in NostrSocialConfig.
     */
    suspend fun uploadToConfiguredServer(
        fileData: ByteArray,
        fileName: String,
        mimeType: String,
        description: String = "",
    ): Response<NostrMedia>

    /**
     * Upload a file to a NIP-96 compatible media server.
     * @param serverUrl The NIP-96 server base URL (e.g., "https://nostr.build")
     * @param fileData The file content as bytes
     * @param fileName The file name
     * @param mimeType The MIME type (e.g., "image/png")
     * @param description Optional alt text / description
     */
    suspend fun upload(
        serverUrl: String,
        fileData: ByteArray,
        fileName: String,
        mimeType: String,
        description: String = "",
    ): Response<NostrMedia>

    /**
     * Get the NIP-96 server info to discover upload endpoint.
     * @param serverUrl The NIP-96 server base URL
     * @return The upload API URL
     */
    suspend fun getServerInfo(serverUrl: String): Response<String>

    /**
     * Upload a file to the configured media server and publish a kind:1 note for it.
     *
     * The media URL is appended to the content unless already present. An NIP-92
     * imeta tag is added alongside any caller-provided tags.
     */
    suspend fun uploadAndPost(
        fileData: ByteArray,
        fileName: String,
        mimeType: String,
        content: String = "",
        description: String = "",
        tags: List<List<String>> = listOf(),
        contentWarning: String? = null,
        expiry: Long? = null,
        sensitive: Boolean = false,
    ): Response<NostrMediaPost>

    /**
     * Upload every file before publishing one kind:1 note containing all media.
     *
     * No event is published if any upload fails. If relay publication fails after
     * uploading, the uploaded files remain on the media server.
     */
    suspend fun uploadManyAndPost(
        uploads: List<NostrMediaUpload>,
        content: String = "",
        tags: List<List<String>> = listOf(),
        contentWarning: String? = null,
        expiry: Long? = null,
        sensitive: Boolean = false,
    ): Response<NostrMediaPost>

    /**
     * Upload every file before publishing one NIP-10 reply containing all media.
     *
     * No event is published if any upload fails. If relay publication fails after
     * uploading, the uploaded files remain on the media server.
     */
    suspend fun uploadAndReply(
        uploads: List<NostrMediaUpload>,
        replyToEventId: String,
        content: String = "",
        rootEventId: String? = null,
        tags: List<List<String>> = listOf(),
        contentWarning: String? = null,
        expiry: Long? = null,
        sensitive: Boolean = false,
    ): Response<NostrMediaPost>

    /** Publish file metadata event (NIP-94, kind:1063) */
    suspend fun publishFileMetadata(
        url: String,
        mimeType: String,
        sha256: String? = null,
        sizeBytes: Long? = null,
        dimensions: String? = null,
        blurhash: String? = null,
        thumbnailUrl: String? = null,
        description: String? = null,
    ): Response<NostrEvent>

    /** Get file metadata for a URL */
    suspend fun getFileMetadata(url: String): Response<NostrFileMetadata?>

    @JsExport.Ignore
    fun publishFileMetadataBlocking(
        url: String,
        mimeType: String,
        sha256: String? = null,
        sizeBytes: Long? = null,
        dimensions: String? = null,
        blurhash: String? = null,
        thumbnailUrl: String? = null,
        description: String? = null,
    ): Response<NostrEvent>

    @JsExport.Ignore
    fun getFileMetadataBlocking(url: String): Response<NostrFileMetadata?>

    @JsExport.Ignore
    fun uploadToConfiguredServerBlocking(
        fileData: ByteArray,
        fileName: String,
        mimeType: String,
        description: String = "",
    ): Response<NostrMedia>

    @JsExport.Ignore
    fun uploadBlocking(
        serverUrl: String,
        fileData: ByteArray,
        fileName: String,
        mimeType: String,
        description: String = "",
    ): Response<NostrMedia>

    @JsExport.Ignore
    fun getServerInfoBlocking(serverUrl: String): Response<String>

    @JsExport.Ignore
    fun uploadAndPostBlocking(
        fileData: ByteArray,
        fileName: String,
        mimeType: String,
        content: String = "",
        description: String = "",
        tags: List<List<String>> = listOf(),
        contentWarning: String? = null,
        expiry: Long? = null,
        sensitive: Boolean = false,
    ): Response<NostrMediaPost>

    @JsExport.Ignore
    fun uploadManyAndPostBlocking(
        uploads: List<NostrMediaUpload>,
        content: String = "",
        tags: List<List<String>> = listOf(),
        contentWarning: String? = null,
        expiry: Long? = null,
        sensitive: Boolean = false,
    ): Response<NostrMediaPost>

    @JsExport.Ignore
    fun uploadAndReplyBlocking(
        uploads: List<NostrMediaUpload>,
        replyToEventId: String,
        content: String = "",
        rootEventId: String? = null,
        tags: List<List<String>> = listOf(),
        contentWarning: String? = null,
        expiry: Long? = null,
        sensitive: Boolean = false,
    ): Response<NostrMediaPost>
}
