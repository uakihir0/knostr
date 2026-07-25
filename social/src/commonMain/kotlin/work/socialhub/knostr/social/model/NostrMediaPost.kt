package work.socialhub.knostr.social.model

import work.socialhub.knostr.entity.NostrEvent
import kotlin.js.JsExport

/** Result of uploading media and publishing it in a text note. */
@JsExport
class NostrMediaPost(
    var media: NostrMedia,
    var event: NostrEvent,
) {
    /** All uploaded media. For the single-image API this contains [media] only. */
    var medias: List<NostrMedia> = listOf(media)
}
