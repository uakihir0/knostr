package work.socialhub.knostr.social.internal

import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.social.api.ArticleResource
import work.socialhub.knostr.social.model.NostrArticle
import work.socialhub.knostr.util.toBlocking
import kotlin.time.Clock

class ArticleResourceImpl(
    private val nostr: Nostr,
) : ArticleResource {

    override suspend fun publishArticle(
        identifier: String,
        title: String,
        content: String,
        summary: String,
        image: String?,
        hashtags: List<String>,
        publishedAt: Long?,
    ): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to publish article")

        val tags = mutableListOf<List<String>>()
        tags.add(listOf("d", identifier))
        tags.add(listOf("title", title))
        if (summary.isNotEmpty()) {
            tags.add(listOf("summary", summary))
        }
        if (image != null) {
            tags.add(listOf("image", image))
        }
        val effectivePublishedAt = publishedAt ?: Clock.System.now().epochSeconds
        tags.add(listOf("published_at", effectivePublishedAt.toString()))
        for (hashtag in hashtags) {
            tags.add(listOf("t", hashtag.lowercase()))
        }

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.LONG_FORM,
            tags = tags,
            content = content,
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun getArticle(pubkey: String, identifier: String): Response<NostrArticle> {
        val filter = NostrFilter(
            authors = listOf(pubkey),
            kinds = listOf(EventKind.LONG_FORM),
            dTags = listOf(identifier),
            limit = 1,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val event = response.data.firstOrNull()
            ?: throw NostrException("Article not found: $identifier")
        return Response(toArticle(event))
    }

    override suspend fun getUserArticles(pubkey: String, since: Long?, until: Long?, limit: Int): Response<List<NostrArticle>> {
        val filter = NostrFilter(
            authors = listOf(pubkey),
            kinds = listOf(EventKind.LONG_FORM),
            since = since,
            until = until,
            limit = limit,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val articles = response.data
            .sortedByDescending { it.createdAt }
            .distinctBy { e -> e.tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.get(1) }
            .map { toArticle(it) }
        return Response(articles)
    }

    override suspend fun deleteArticle(identifier: String, reason: String): Response<Boolean> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to delete article")

        // Find the article event to get its ID
        val filter = NostrFilter(
            authors = listOf(signer.getPublicKey()),
            kinds = listOf(EventKind.LONG_FORM),
            dTags = listOf(identifier),
            limit = 1,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val event = response.data.firstOrNull()
            ?: throw NostrException("Article not found: $identifier")

        return nostr.events().deleteEvent(event.id, reason)
    }

    private fun toArticle(event: NostrEvent): NostrArticle {
        val article = NostrArticle()
        article.event = event
        article.content = event.content
        article.createdAt = event.createdAt

        val hashtags = mutableListOf<String>()
        for (tag in event.tags) {
            if (tag.size < 2) continue
            when (tag[0]) {
                "d" -> article.identifier = tag[1]
                "title" -> article.title = tag[1]
                "summary" -> article.summary = tag[1]
                "image" -> article.image = tag[1]
                "published_at" -> article.publishedAt = tag[1].toLongOrNull()
                "t" -> hashtags.add(tag[1])
            }
        }
        article.hashtags = hashtags

        return article
    }

    override fun publishArticleBlocking(
        identifier: String,
        title: String,
        content: String,
        summary: String,
        image: String?,
        hashtags: List<String>,
        publishedAt: Long?,
    ): Response<NostrEvent> {
        return toBlocking { publishArticle(identifier, title, content, summary, image, hashtags, publishedAt) }
    }

    override fun getArticleBlocking(pubkey: String, identifier: String): Response<NostrArticle> {
        return toBlocking { getArticle(pubkey, identifier) }
    }

    override fun getUserArticlesBlocking(pubkey: String, since: Long?, until: Long?, limit: Int): Response<List<NostrArticle>> {
        return toBlocking { getUserArticles(pubkey, since, until, limit) }
    }

    override fun deleteArticleBlocking(identifier: String, reason: String): Response<Boolean> {
        return toBlocking { deleteArticle(identifier, reason) }
    }
}
