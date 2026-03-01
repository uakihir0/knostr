package work.socialhub.knostr

import kotlin.js.JsExport

@JsExport
object EventKind {
    const val METADATA = 0
    const val TEXT_NOTE = 1
    const val RECOMMEND_RELAY = 2
    const val FOLLOW_LIST = 3
    const val ENCRYPTED_DM = 4
    const val EVENT_DELETION = 5
    const val REPOST = 6
    const val REACTION = 7
    const val GENERIC_REPOST = 16
    const val SEAL = 13
    const val CHAT_MESSAGE = 14
    const val FILE_MESSAGE = 15
    const val GIFT_WRAP = 1059
    const val ZAP_REQUEST = 9734
    const val ZAP_RECEIPT = 9735
    const val FILE_METADATA = 1063
    const val MUTE_LIST = 10000
    const val PIN_LIST = 10001
    const val AUTH = 22242
    const val RELAY_LIST = 10002
    const val DM_RELAY_LIST = 10050
    const val BOOKMARK_LIST = 10003
}
