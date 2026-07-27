package work.socialhub.knostr.social.model

import work.socialhub.knostr.entity.NostrEvent
import kotlin.js.JsExport

/** Result of uploading media and publishing it in a text note. */
@JsExport
class NostrMediaPost(
    media: NostrMedia,
    var event: NostrEvent,
) {
    private var mediaList: List<NostrMedia> = listOf(media)

    /** First uploaded media. */
    val media: NostrMedia
        get() = mediaList.first()

    /** All uploaded media. For the single-image API this contains [media] only. */
    val medias: List<NostrMedia>
        get() = mediaList

    internal fun replaceMedias(medias: List<NostrMedia>) {
        require(medias.isNotEmpty()) { "At least one media item is required" }
        mediaList = medias.toList()
    }
}
