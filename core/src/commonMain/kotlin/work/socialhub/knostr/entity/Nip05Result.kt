package work.socialhub.knostr.entity

import kotlinx.serialization.Serializable

@Serializable
data class Nip05Result(
    val names: Map<String, String> = mapOf(),
    val relays: Map<String, List<String>>? = null,
)
