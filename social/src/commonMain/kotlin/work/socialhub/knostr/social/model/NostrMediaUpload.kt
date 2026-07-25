package work.socialhub.knostr.social.model

import kotlin.js.JsExport

/** File input for configured NIP-96 upload operations. */
@JsExport
class NostrMediaUpload(
    var fileData: ByteArray,
    var fileName: String,
    var mimeType: String,
    var description: String = "",
)
