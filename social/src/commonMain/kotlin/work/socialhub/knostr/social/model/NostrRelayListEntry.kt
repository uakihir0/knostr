package work.socialhub.knostr.social.model

import kotlin.js.JsExport

@JsExport
data class NostrRelayListEntry(
    val url: String,
    val read: Boolean = true,
    val write: Boolean = true,
)
