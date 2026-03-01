package work.socialhub.knostr.social.model

import work.socialhub.knostr.entity.NostrEvent
import kotlin.js.JsExport

@JsExport
class NostrPoll {
    lateinit var event: NostrEvent
    var content: String = ""
    var options: List<NostrPollOption> = listOf()
    var createdAt: Long = 0
    /** Closing timestamp (epoch seconds, null = no close) */
    var closedAt: Long? = null
}

@JsExport
data class NostrPollOption(
    val index: Int,
    val label: String,
)
