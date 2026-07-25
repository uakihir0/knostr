package work.socialhub.knostr.social.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
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
import work.socialhub.knostr.social.NostrMediaTags
import work.socialhub.knostr.social.NostrSocialConfig
import work.socialhub.knostr.social.api.FeedResource
import work.socialhub.knostr.social.api.MediaResource
import work.socialhub.knostr.social.model.NostrFileMetadata
import work.socialhub.knostr.social.model.NostrMedia
import work.socialhub.knostr.social.model.NostrMediaPost
import work.socialhub.knostr.social.model.NostrMediaUpload
import work.socialhub.knostr.util.toBlocking
import work.socialhub.khttpclient.HttpRequest
import kotlin.time.Clock

internal interface MediaEventPublisher {
    suspend fun post(
        content: String,
        tags: List<List<String>>,
        contentWarning: String?,
        expiry: Long?,
        sensitive: Boolean,
    ): Response<NostrEvent>

    suspend fun reply(
        content: String,
        replyToEventId: String,
        rootEventId: String?,
        tags: List<List<String>>,
        contentWarning: String?,
        expiry: Long?,
        sensitive: Boolean,
    ): Response<NostrEvent>
}

private class FeedMediaEventPublisher(
    private val feed: FeedResource,
) : MediaEventPublisher {
    override suspend fun post(
        content: String,
        tags: List<List<String>>,
        contentWarning: String?,
        expiry: Long?,
        sensitive: Boolean,
    ) = feed.post(content, tags, contentWarning, expiry, sensitive)

    override suspend fun reply(
        content: String,
        replyToEventId: String,
        rootEventId: String?,
        tags: List<List<String>>,
        contentWarning: String?,
        expiry: Long?,
        sensitive: Boolean,
    ) = feed.reply(
        content,
        replyToEventId,
        rootEventId,
        contentWarning,
        expiry,
        sensitive,
        tags,
    )
}

class MediaResourceImpl private constructor(
    private val nostr: Nostr,
    private val config: NostrSocialConfig,
    private val eventPublisher: MediaEventPublisher,
    private val configuredUploader: (suspend (NostrMediaUpload) -> Response<NostrMedia>)?,
) : MediaResource {

    constructor(
        nostr: Nostr,
        config: NostrSocialConfig = NostrSocialConfig(),
        feed: FeedResource = FeedResourceImpl(nostr, config),
    ) : this(nostr, config, FeedMediaEventPublisher(feed), null)

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun getServerInfo(serverUrl: String): Response<String> {
        val infoUrl = serverUrl.trimEnd('/') + "/.well-known/nostr/nip96.json"
        val response = HttpRequest()
            .url(infoUrl)
            .accept("application/json")
            .get()

        val body = response.stringBody
        if (body.isBlank()) {
            throw NostrException("Failed to fetch NIP-96 server info")
        }

        val jsonObj = json.parseToJsonElement(body).jsonObject
        val apiUrl = jsonObj["api_url"]?.jsonPrimitive?.content
            ?: throw NostrException("NIP-96 server info missing api_url")

        return Response(apiUrl)
    }

    override suspend fun uploadToConfiguredServer(
        fileData: ByteArray,
        fileName: String,
        mimeType: String,
        description: String,
    ): Response<NostrMedia> {
        return upload(configuredServerUrl(), fileData, fileName, mimeType, description)
    }

    override suspend fun upload(
        serverUrl: String,
        fileData: ByteArray,
        fileName: String,
        mimeType: String,
        description: String,
    ): Response<NostrMedia> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to upload media")

        // Get the upload endpoint
        val apiUrl = getServerInfo(serverUrl).data

        // Create NIP-98 HTTP Auth event
        val authEvent = createHttpAuthEvent(apiUrl, "POST")

        val authEventJson = InternalUtility.toJson(authEvent)
        val authHeader = "Nostr ${encodeBase64(authEventJson.encodeToByteArray())}"

        val request = HttpRequest()
            .url(apiUrl)
            .header("Authorization", authHeader)
            .file("file", fileName, fileData)
            .param("content-type", mimeType)

        if (description.isNotEmpty()) {
            request.param("alt", description)
        }

        val response = request.post()
        val body = response.stringBody
        if (body.isBlank()) {
            throw NostrException("Failed to upload media")
        }

        val jsonObj = json.parseToJsonElement(body).jsonObject
        val status = jsonObj["status"]?.jsonPrimitive?.content

        if (status != "success") {
            val message = jsonObj["message"]?.jsonPrimitive?.content ?: "Unknown error"
            throw NostrException("Media upload failed: $message")
        }

        val nip94Event = jsonObj["nip94_event"]?.jsonObject

        val media = NostrMedia()
        media.fileName = fileName
        media.mimeType = mimeType
        media.alt = description.ifEmpty { null }

        if (nip94Event != null) {
            val tags = nip94Event["tags"]?.jsonArray
            if (tags != null) {
                for (tagElement in tags) {
                    val tag = tagElement.jsonArray
                    if (tag.size < 2) continue
                    val key = tag[0].jsonPrimitive.content
                    val value = tag[1].jsonPrimitive.content
                    when (key) {
                        "url" -> media.url = value
                        "x", "ox" -> media.sha256 = value
                        "size" -> media.sizeBytes = value.toLongOrNull()
                        "dim" -> {
                            val parts = value.split("x")
                            if (parts.size == 2) {
                                media.width = parts[0].toIntOrNull()
                                media.height = parts[1].toIntOrNull()
                            }
                        }
                        "blurhash" -> media.blurhash = value
                        "thumb" -> media.thumbnailUrl = value
                        "m" -> media.mimeType = value
                    }
                }
            }
        }

        // Fallback: check for direct URL in response
        if (media.url.isEmpty()) {
            jsonObj["url"]?.jsonPrimitive?.content?.let { media.url = it }
        }
        if (media.url.isEmpty()) {
            throw NostrException("Media upload response missing URL")
        }

        return Response(media)
    }

    override suspend fun uploadAndPost(
        fileData: ByteArray,
        fileName: String,
        mimeType: String,
        content: String,
        description: String,
        tags: List<List<String>>,
        contentWarning: String?,
        expiry: Long?,
        sensitive: Boolean,
    ): Response<NostrMediaPost> {
        return uploadManyAndPost(
            uploads = listOf(NostrMediaUpload(fileData, fileName, mimeType, description)),
            content = content,
            tags = tags,
            contentWarning = contentWarning,
            expiry = expiry,
            sensitive = sensitive,
        )
    }

    override suspend fun uploadManyAndPost(
        uploads: List<NostrMediaUpload>,
        content: String,
        tags: List<List<String>>,
        contentWarning: String?,
        expiry: Long?,
        sensitive: Boolean,
    ): Response<NostrMediaPost> {
        val medias = uploadAll(uploads)
        val postResponse = eventPublisher.post(
            content = appendMediaUrls(content, medias.map { it.url }),
            tags = tags + medias.map { NostrMediaTags.imeta(it) },
            contentWarning = contentWarning,
            expiry = expiry,
            sensitive = sensitive,
        )
        return mediaPostResponse(medias, postResponse)
    }

    override suspend fun uploadAndReply(
        uploads: List<NostrMediaUpload>,
        replyToEventId: String,
        content: String,
        rootEventId: String?,
        tags: List<List<String>>,
        contentWarning: String?,
        expiry: Long?,
        sensitive: Boolean,
    ): Response<NostrMediaPost> {
        val medias = uploadAll(uploads)
        val replyResponse = eventPublisher.reply(
            content = appendMediaUrls(content, medias.map { it.url }),
            replyToEventId = replyToEventId,
            rootEventId = rootEventId,
            tags = tags + medias.map { NostrMediaTags.imeta(it) },
            contentWarning = contentWarning,
            expiry = expiry,
            sensitive = sensitive,
        )
        return mediaPostResponse(medias, replyResponse)
    }

    override suspend fun publishFileMetadata(
        url: String,
        mimeType: String,
        sha256: String?,
        sizeBytes: Long?,
        dimensions: String?,
        blurhash: String?,
        thumbnailUrl: String?,
        description: String?,
    ): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to publish file metadata")

        val tags = mutableListOf<List<String>>()
        tags.add(listOf("url", url))
        tags.add(listOf("m", mimeType))
        if (sha256 != null) tags.add(listOf("x", sha256))
        if (sizeBytes != null) tags.add(listOf("size", sizeBytes.toString()))
        if (dimensions != null) tags.add(listOf("dim", dimensions))
        if (blurhash != null) tags.add(listOf("blurhash", blurhash))
        if (thumbnailUrl != null) tags.add(listOf("thumb", thumbnailUrl))
        if (description != null) tags.add(listOf("alt", description))

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.FILE_METADATA,
            tags = tags,
            content = description ?: "",
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun getFileMetadata(url: String): Response<NostrFileMetadata?> {
        val filter = NostrFilter(
            kinds = listOf(EventKind.FILE_METADATA),
            limit = 50,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val event = response.data.firstOrNull { e ->
            e.tags.any { it.size >= 2 && it[0] == "url" && it[1] == url }
        }
        if (event == null) return response.withData(null)

        val metadata = NostrFileMetadata()
        metadata.event = event
        metadata.createdAt = event.createdAt
        metadata.description = event.content.ifEmpty { null }
        for (tag in event.tags) {
            if (tag.size < 2) continue
            when (tag[0]) {
                "url" -> metadata.url = tag[1]
                "m" -> metadata.mimeType = tag[1]
                "x" -> metadata.sha256 = tag[1]
                "size" -> metadata.sizeBytes = tag[1].toLongOrNull()
                "dim" -> metadata.dimensions = tag[1]
                "blurhash" -> metadata.blurhash = tag[1]
                "thumb" -> metadata.thumbnailUrl = tag[1]
                "alt" -> if (metadata.description == null) metadata.description = tag[1]
            }
        }
        return response.withData(metadata)
    }

    private fun createHttpAuthEvent(url: String, method: String): work.socialhub.knostr.entity.NostrEvent {
        val signer = nostr.signer()!!
        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = 27235, // NIP-98 HTTP Auth
            tags = listOf(
                listOf("u", url),
                listOf("method", method),
            ),
            content = "",
        )
        return signer.sign(unsigned)
    }

    override fun uploadToConfiguredServerBlocking(
        fileData: ByteArray,
        fileName: String,
        mimeType: String,
        description: String,
    ): Response<NostrMedia> {
        return toBlocking { uploadToConfiguredServer(fileData, fileName, mimeType, description) }
    }

    override fun uploadBlocking(
        serverUrl: String,
        fileData: ByteArray,
        fileName: String,
        mimeType: String,
        description: String,
    ): Response<NostrMedia> {
        return toBlocking { upload(serverUrl, fileData, fileName, mimeType, description) }
    }

    override fun getServerInfoBlocking(serverUrl: String): Response<String> {
        return toBlocking { getServerInfo(serverUrl) }
    }

    override fun uploadAndPostBlocking(
        fileData: ByteArray,
        fileName: String,
        mimeType: String,
        content: String,
        description: String,
        tags: List<List<String>>,
        contentWarning: String?,
        expiry: Long?,
        sensitive: Boolean,
    ): Response<NostrMediaPost> {
        return toBlocking {
            uploadAndPost(
                fileData,
                fileName,
                mimeType,
                content,
                description,
                tags,
                contentWarning,
                expiry,
                sensitive,
            )
        }
    }

    override fun uploadManyAndPostBlocking(
        uploads: List<NostrMediaUpload>,
        content: String,
        tags: List<List<String>>,
        contentWarning: String?,
        expiry: Long?,
        sensitive: Boolean,
    ): Response<NostrMediaPost> {
        return toBlocking {
            uploadManyAndPost(uploads, content, tags, contentWarning, expiry, sensitive)
        }
    }

    override fun uploadAndReplyBlocking(
        uploads: List<NostrMediaUpload>,
        replyToEventId: String,
        content: String,
        rootEventId: String?,
        tags: List<List<String>>,
        contentWarning: String?,
        expiry: Long?,
        sensitive: Boolean,
    ): Response<NostrMediaPost> {
        return toBlocking {
            uploadAndReply(
                uploads,
                replyToEventId,
                content,
                rootEventId,
                tags,
                contentWarning,
                expiry,
                sensitive,
            )
        }
    }

    override fun publishFileMetadataBlocking(
        url: String,
        mimeType: String,
        sha256: String?,
        sizeBytes: Long?,
        dimensions: String?,
        blurhash: String?,
        thumbnailUrl: String?,
        description: String?,
    ): Response<NostrEvent> {
        return toBlocking { publishFileMetadata(url, mimeType, sha256, sizeBytes, dimensions, blurhash, thumbnailUrl, description) }
    }

    override fun getFileMetadataBlocking(url: String): Response<NostrFileMetadata?> {
        return toBlocking { getFileMetadata(url) }
    }

    companion object {
        private val BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

        internal fun withConfiguredUploader(
            nostr: Nostr,
            config: NostrSocialConfig,
            feed: FeedResource,
            uploader: suspend (NostrMediaUpload) -> Response<NostrMedia>,
        ): MediaResourceImpl {
            return MediaResourceImpl(
                nostr,
                config,
                FeedMediaEventPublisher(feed),
                uploader,
            )
        }

        internal fun withDependencies(
            nostr: Nostr,
            config: NostrSocialConfig,
            eventPublisher: MediaEventPublisher,
            uploader: suspend (NostrMediaUpload) -> Response<NostrMedia>,
        ): MediaResourceImpl {
            return MediaResourceImpl(nostr, config, eventPublisher, uploader)
        }

        internal fun appendMediaUrls(content: String, mediaUrls: List<String>): String {
            var result = content
            for (mediaUrl in mediaUrls.distinct()) {
                if (mediaUrl !in result) {
                    result = if (result.isBlank()) mediaUrl else "$result\n$mediaUrl"
                }
            }
            return result
        }

        internal fun configuredServerUrl(config: NostrSocialConfig): String {
            return config.mediaUploadServerUrl.trim().trimEnd('/').also {
                if (it.isEmpty()) {
                    throw NostrException("Media upload server URL must not be blank")
                }
            }
        }

        fun encodeBase64(data: ByteArray): String {
            val sb = StringBuilder()
            var i = 0
            while (i < data.size) {
                val b0 = data[i].toInt() and 0xFF
                val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
                val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else 0
                val remaining = data.size - i

                sb.append(BASE64_CHARS[(b0 shr 2) and 0x3F])
                sb.append(BASE64_CHARS[((b0 shl 4) or (b1 shr 4)) and 0x3F])
                sb.append(if (remaining > 1) BASE64_CHARS[((b1 shl 2) or (b2 shr 6)) and 0x3F] else '=')
                sb.append(if (remaining > 2) BASE64_CHARS[b2 and 0x3F] else '=')
                i += 3
            }
            return sb.toString()
        }
    }

    private suspend fun uploadAll(uploads: List<NostrMediaUpload>): List<NostrMedia> {
        if (uploads.isEmpty()) {
            throw NostrException("At least one media upload is required")
        }
        return uploads.map { input ->
            val response = configuredUploader?.invoke(input)
                ?: uploadToConfiguredServer(
                    input.fileData,
                    input.fileName,
                    input.mimeType,
                    input.description,
                )
            response.data.apply {
                if (url.isBlank()) {
                    throw NostrException("Media upload response missing URL")
                }
                if (fileName == null) fileName = input.fileName
                if (mimeType == null) mimeType = input.mimeType
                if (alt == null && input.description.isNotEmpty()) alt = input.description
            }
        }
    }

    private fun mediaPostResponse(
        medias: List<NostrMedia>,
        eventResponse: Response<NostrEvent>,
    ): Response<NostrMediaPost> {
        val result = NostrMediaPost(medias.first(), eventResponse.data).apply {
            this.medias = medias
        }
        return Response(result).also {
            it.json = eventResponse.json
            it.isComplete = eventResponse.isComplete
        }
    }

    private fun configuredServerUrl(): String = configuredServerUrl(config)
}
