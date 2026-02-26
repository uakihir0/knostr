package work.socialhub.knostr.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.js.JsExport

@JsExport
@Serializable
data class NostrEvent(
    val id: String,
    val pubkey: String,
    @SerialName("created_at")
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>> = listOf(),
    val content: String,
    val sig: String,
)
