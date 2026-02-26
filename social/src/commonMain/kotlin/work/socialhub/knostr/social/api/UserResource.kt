package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrProfile
import work.socialhub.knostr.social.model.NostrUser
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

    /** Verify a NIP-05 address */
    suspend fun verifyNip05(address: String): Response<Boolean>

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
    fun verifyNip05Blocking(address: String): Response<Boolean>
}
