package work.socialhub.knostr.social

import kotlin.js.JsExport

@JsExport
class NostrSocialConfig {

    /** フォローリスト (kind:3) のキャッシュを有効にする */
    var cacheFollowList: Boolean = true

    /** フォローリストキャッシュの有効期限 (ミリ秒) */
    var followListCacheTtlMs: Long = 300_000

    /** ユーザープロフィール (kind:0) のキャッシュを有効にする */
    var cacheUserProfile: Boolean = true

    /** ユーザープロフィールキャッシュの有効期限 (ミリ秒) */
    var userProfileCacheTtlMs: Long = 1_800_000
}
