package work.socialhub.knostr.social.model

import kotlin.js.JsExport

@JsExport
class NostrMedia {
    /** URL of the uploaded file */
    var url: String = ""
    /** Original file name */
    var fileName: String? = null
    /** MIME type */
    var mimeType: String? = null
    /** File size in bytes */
    var sizeBytes: Long? = null
    /** SHA-256 hash of the file */
    var sha256: String? = null
    /** Dimensions (for images/videos) */
    var width: Int? = null
    var height: Int? = null
    /** Blurhash (for images) */
    var blurhash: String? = null
    /** Thumbnail URL */
    var thumbnailUrl: String? = null
}
