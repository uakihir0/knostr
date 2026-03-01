package work.socialhub.knostr.social.model

import work.socialhub.knostr.entity.NostrEvent
import kotlin.js.JsExport

@JsExport
class NostrArticle {
    lateinit var event: NostrEvent
    /** d-tag identifier */
    var identifier: String = ""
    var title: String = ""
    var summary: String = ""
    /** Markdown content */
    var content: String = ""
    var image: String? = null
    var publishedAt: Long? = null
    var hashtags: List<String> = listOf()
    var createdAt: Long = 0
}
