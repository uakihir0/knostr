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
    /** NIP-40: article expiry timestamp (null if no expiry) */
    var expiry: Long? = null
    /** NIP-36: whether this article is marked as sensitive */
    var isSensitive: Boolean = false
}
