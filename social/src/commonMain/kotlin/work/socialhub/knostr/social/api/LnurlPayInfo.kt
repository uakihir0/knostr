package work.socialhub.knostr.social.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.js.JsExport

@JsExport
@Serializable
data class LnurlPayInfo(
    val callback: String = "",
    val minSendable: Long = 0,
    val maxSendable: Long = 0,
    @SerialName("allowsNostr")
    val allowsNostr: Boolean = false,
    @SerialName("nostrPubkey")
    val nostrPubkey: String? = null,
)
