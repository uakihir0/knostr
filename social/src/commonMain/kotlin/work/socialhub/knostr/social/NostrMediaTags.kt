package work.socialhub.knostr.social

import work.socialhub.knostr.NostrException
import work.socialhub.knostr.social.model.NostrMedia
import kotlin.js.JsExport

/** Builders for standard Nostr media tags. */
@JsExport
object NostrMediaTags {

    /** Build an NIP-92 imeta tag for uploaded media. */
    fun imeta(media: NostrMedia): List<String> {
        if (media.url.isBlank()) {
            throw NostrException("Media URL must not be blank")
        }
        return buildList {
            add("imeta")
            add("url ${media.url}")
            media.mimeType?.let { add("m $it") }
            media.sha256?.let { add("x $it") }
            media.sizeBytes?.let { add("size $it") }
            if (media.width != null && media.height != null) {
                add("dim ${media.width}x${media.height}")
            }
            media.blurhash?.let { add("blurhash $it") }
            media.thumbnailUrl?.let { add("thumb $it") }
            media.alt?.let { add("alt $it") }
        }
    }
}
