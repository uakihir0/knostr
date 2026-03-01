package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrProfile
import work.socialhub.knostr.social.model.NostrUser
import work.socialhub.knostr.social.model.NostrUserStatus
import kotlin.js.JsExport

@JsExport
interface UserResource {

    /** Get a user's profile */
    suspend fun getProfile(pubkey: String): Response<NostrUser>

    /** Update own profile (kind:0) */
    suspend fun updateProfile(profile: NostrProfile): Response<NostrEvent>

    /** Follow a user (update kind:3 follow list) */
    suspend fun follow(pubkey: String): Response<NostrEvent>

    /** Unfollow a user (update kind:3 follow list) */
    suspend fun unfollow(pubkey: String): Response<NostrEvent>

    /** Get list of pubkeys the user is following */
    suspend fun getFollowing(pubkey: String): Response<List<String>>

    /** Get pubkeys of users who follow the given user (kind:3 reverse lookup) */
    suspend fun getFollowers(pubkey: String, limit: Int = 100): Response<List<String>>

    /** Get profiles for multiple pubkeys in a single query */
    suspend fun getProfiles(pubkeys: List<String>): Response<List<NostrUser>>

    /** Verify a NIP-05 address */
    suspend fun verifyNip05(address: String): Response<Boolean>

    /** Set user status (NIP-315, kind:30315) */
    suspend fun setStatus(content: String, type: String = "general", url: String? = null, expiration: Long? = null): Response<NostrEvent>

    /** Get user status */
    suspend fun getStatus(pubkey: String, type: String = "general"): Response<NostrUserStatus?>

    /** Clear user status */
    suspend fun clearStatus(type: String = "general"): Response<NostrEvent>

    @JsExport.Ignore
    fun getProfileBlocking(pubkey: String): Response<NostrUser>

    @JsExport.Ignore
    fun updateProfileBlocking(profile: NostrProfile): Response<NostrEvent>

    @JsExport.Ignore
    fun followBlocking(pubkey: String): Response<NostrEvent>

    @JsExport.Ignore
    fun unfollowBlocking(pubkey: String): Response<NostrEvent>

    @JsExport.Ignore
    fun getFollowingBlocking(pubkey: String): Response<List<String>>

    @JsExport.Ignore
    fun getFollowersBlocking(pubkey: String, limit: Int = 100): Response<List<String>>

    @JsExport.Ignore
    fun getProfilesBlocking(pubkeys: List<String>): Response<List<NostrUser>>

    @JsExport.Ignore
    fun verifyNip05Blocking(address: String): Response<Boolean>

    @JsExport.Ignore
    fun setStatusBlocking(content: String, type: String = "general", url: String? = null, expiration: Long? = null): Response<NostrEvent>

    @JsExport.Ignore
    fun getStatusBlocking(pubkey: String, type: String = "general"): Response<NostrUserStatus?>

    @JsExport.Ignore
    fun clearStatusBlocking(type: String = "general"): Response<NostrEvent>
}
