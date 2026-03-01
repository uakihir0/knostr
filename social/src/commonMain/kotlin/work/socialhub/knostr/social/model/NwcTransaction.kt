package work.socialhub.knostr.social.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.js.JsExport

@JsExport
@Serializable
data class NwcTransaction(
    val type: String = "",
    val invoice: String = "",
    val description: String = "",
    val preimage: String = "",
    @SerialName("payment_hash")
    val paymentHash: String = "",
    val amount: Long = 0,
    @SerialName("fees_paid")
    val feesPaid: Long = 0,
    @SerialName("created_at")
    val createdAt: Long = 0,
    @SerialName("settled_at")
    val settledAt: Long? = null,
)
