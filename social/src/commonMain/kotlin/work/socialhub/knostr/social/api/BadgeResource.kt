package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.social.model.NostrBadge
import work.socialhub.knostr.social.model.NostrBadgeAward
import kotlin.js.JsExport

@JsExport
interface BadgeResource {

    /** Define a badge (NIP-58, kind:30009) */
    suspend fun defineBadge(
        dTag: String,
        name: String,
        description: String = "",
        image: String = "",
        thumbImage: String = "",
    ): Response<NostrEvent>

    /** Award a badge to recipients (NIP-58, kind:8) */
    suspend fun awardBadge(
        badgeDTag: String,
        recipientPubkeys: List<String>,
    ): Response<NostrEvent>

    /** Set profile badges (NIP-58, kind:30008) */
    suspend fun setProfileBadges(
        badgeRefs: List<Pair<String, String>>,
    ): Response<NostrEvent>

    /** Get badge definition by author pubkey and d-tag */
    suspend fun getBadgeDefinition(pubkey: String, dTag: String): Response<NostrBadge>

    /** Get profile badges for a user */
    suspend fun getProfileBadges(pubkey: String): Response<List<NostrBadge>>

    @JsExport.Ignore
    fun defineBadgeBlocking(
        dTag: String,
        name: String,
        description: String = "",
        image: String = "",
        thumbImage: String = "",
    ): Response<NostrEvent>

    @JsExport.Ignore
    fun awardBadgeBlocking(
        badgeDTag: String,
        recipientPubkeys: List<String>,
    ): Response<NostrEvent>

    @JsExport.Ignore
    fun setProfileBadgesBlocking(
        badgeRefs: List<Pair<String, String>>,
    ): Response<NostrEvent>

    @JsExport.Ignore
    fun getBadgeDefinitionBlocking(pubkey: String, dTag: String): Response<NostrBadge>

    @JsExport.Ignore
    fun getProfileBadgesBlocking(pubkey: String): Response<List<NostrBadge>>
}
