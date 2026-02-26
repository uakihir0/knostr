package work.socialhub.knostr.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.js.JsExport

@JsExport
@Serializable
data class NostrFilter(
    val ids: List<String>? = null,
    val authors: List<String>? = null,
    val kinds: List<Int>? = null,
    val since: Long? = null,
    val until: Long? = null,
    @SerialName("#e")
    val eTags: List<String>? = null,
    @SerialName("#p")
    val pTags: List<String>? = null,
    val limit: Int? = null,
    val search: String? = null,
)
