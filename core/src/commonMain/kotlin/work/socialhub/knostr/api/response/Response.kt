package work.socialhub.knostr.api.response

import kotlin.js.JsExport

@JsExport
class Response<T>(
    var data: T,
) {
    var json: String? = null

    /** False when a query returned partial data because it timed out before EOSE. */
    var isComplete: Boolean = true
}
