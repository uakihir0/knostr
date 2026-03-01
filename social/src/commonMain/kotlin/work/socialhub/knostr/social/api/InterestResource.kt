package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import kotlin.js.JsExport

@JsExport
interface InterestResource {

    /** Follow a hashtag (NIP-51 kind:10015) */
    suspend fun followHashtag(hashtag: String): Response<NostrEvent>

    /** Unfollow a hashtag */
    suspend fun unfollowHashtag(hashtag: String): Response<NostrEvent>

    /** Get list of followed hashtags */
    suspend fun getFollowedHashtags(): Response<List<String>>

    @JsExport.Ignore
    fun followHashtagBlocking(hashtag: String): Response<NostrEvent>

    @JsExport.Ignore
    fun unfollowHashtagBlocking(hashtag: String): Response<NostrEvent>

    @JsExport.Ignore
    fun getFollowedHashtagsBlocking(): Response<List<String>>
}
