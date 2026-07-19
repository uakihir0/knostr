package work.socialhub.knostr.social

import work.socialhub.knostr.social.api.SocialCache
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

    /** ノートキャッシュの有効期限 (ミリ秒) */
    var noteCacheTtlMs: Long = 3_600_000

    /** ノート統計キャッシュの有効期限 (ミリ秒) */
    var noteStatsCacheTtlMs: Long = 60_000

    /** Optional application-provided social cache. */
    var socialCache: SocialCache? = null

    /** Whether missing social data should be resolved in the background. */
    var deferredEnrichmentEnabled: Boolean = true

    /** Number of background relay attempts after the synchronous request. */
    var deferredEnrichmentMaxAttempts: Int = 3

    /** Delay before the first background attempt. */
    var deferredEnrichmentInitialDelayMs: Long = 1_000

    /** Multiplier applied to the delay after each failed attempt. */
    var deferredEnrichmentBackoffMultiplier: Double = 2.0
}
