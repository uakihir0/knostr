package work.socialhub.knostr.social.model

import kotlin.js.JsExport

@JsExport
data class NostrUserStatus(
    /** Status type: "general" or "music" */
    val type: String,
    /** Status content text */
    val content: String,
    /** Optional URL link */
    val url: String? = null,
    /** Expiration timestamp (epoch seconds, null = no expiry) */
    val expiration: Long? = null,
)
