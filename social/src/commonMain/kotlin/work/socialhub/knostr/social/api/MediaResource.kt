package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.social.model.NostrMedia
import kotlin.js.JsExport

@JsExport
interface MediaResource {

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
}
