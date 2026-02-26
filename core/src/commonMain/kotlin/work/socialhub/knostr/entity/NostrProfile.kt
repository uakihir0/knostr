package work.socialhub.knostr.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.js.JsExport

@JsExport
@Serializable
data class NostrProfile(
    val name: String? = null,
    val about: String? = null,
    val picture: String? = null,
    val banner: String? = null,
    val nip05: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    val website: String? = null,
    val lud16: String? = null,
)
