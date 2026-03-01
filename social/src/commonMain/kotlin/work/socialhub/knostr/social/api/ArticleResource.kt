package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.social.model.NostrArticle
import kotlin.js.JsExport

@JsExport
interface ArticleResource {

    /** Publish a long-form article (NIP-23, kind:30023) */
    suspend fun publishArticle(
        identifier: String,
        title: String,
        content: String,
        summary: String = "",
        image: String? = null,
        hashtags: List<String> = listOf(),
        publishedAt: Long? = null,
    ): Response<NostrEvent>

    /** Get an article by author pubkey and d-tag identifier */
    suspend fun getArticle(pubkey: String, identifier: String): Response<NostrArticle>

    /** Get articles by a user */
    suspend fun getUserArticles(pubkey: String, since: Long? = null, until: Long? = null, limit: Int = 20): Response<List<NostrArticle>>

    /** Delete an article */
    suspend fun deleteArticle(identifier: String, reason: String = ""): Response<Boolean>

    @JsExport.Ignore
    fun publishArticleBlocking(
        identifier: String,
        title: String,
        content: String,
        summary: String = "",
        image: String? = null,
        hashtags: List<String> = listOf(),
        publishedAt: Long? = null,
    ): Response<NostrEvent>

    @JsExport.Ignore
    fun getArticleBlocking(pubkey: String, identifier: String): Response<NostrArticle>

    @JsExport.Ignore
    fun getUserArticlesBlocking(pubkey: String, since: Long? = null, until: Long? = null, limit: Int = 20): Response<List<NostrArticle>>

    @JsExport.Ignore
    fun deleteArticleBlocking(identifier: String, reason: String = ""): Response<Boolean>
}
