package work.socialhub.knostr.entity

import kotlin.js.JsExport

@JsExport
sealed class Nip19Entity {
    data class NPub(val pubkey: String) : Nip19Entity()
    data class NSec(val seckey: String) : Nip19Entity()
    data class Note(val eventId: String) : Nip19Entity()
    data class NProfile(val pubkey: String, val relays: List<String> = listOf()) : Nip19Entity()
    data class NEvent(val eventId: String, val relays: List<String> = listOf(), val author: String? = null) : Nip19Entity()
    data class NAddr(val identifier: String, val pubkey: String, val kind: Int, val relays: List<String> = listOf()) : Nip19Entity()
}
