package work.socialhub.knostr.social.model

import work.socialhub.knostr.entity.NostrEvent
import kotlin.js.JsExport

@JsExport
class NostrFileMetadata {
    lateinit var event: NostrEvent
    var url: String = ""
    var mimeType: String = ""
    var sha256: String? = null
    var sizeBytes: Long? = null
    var dimensions: String? = null
    var blurhash: String? = null
    var thumbnailUrl: String? = null
    var description: String? = null
    var createdAt: Long = 0
}
