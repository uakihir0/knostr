package work.socialhub.knostr.social.model

import kotlin.js.JsExport

/** File input for configured NIP-96 upload operations. */
@JsExport
class NostrMediaUpload(
    val fileData: ByteArray,
    val fileName: String,
    val mimeType: String,
    val description: String = "",
)
