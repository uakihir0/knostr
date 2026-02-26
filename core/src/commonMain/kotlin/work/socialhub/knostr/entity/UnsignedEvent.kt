package work.socialhub.knostr.entity

import kotlin.js.JsExport

@JsExport
data class UnsignedEvent(
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>> = listOf(),
    val content: String,
)
