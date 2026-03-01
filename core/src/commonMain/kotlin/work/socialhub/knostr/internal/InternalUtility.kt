package work.socialhub.knostr.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.relay.RelayMessage
import work.socialhub.knostr.util.Hex

object InternalUtility {

    val json = Json {
        explicitNulls = false
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    inline fun <reified T> fromJson(str: String): T {
        return json.decodeFromString(str)
    }

    inline fun <reified T> toJson(obj: T): String {
        return json.encodeToString(obj)
    }

    /**
     * Serialize event fields for ID computation (NIP-01).
     * Format: [0, pubkey, created_at, kind, tags, content]
     */
    fun serializeForId(
        pubkey: String,
        createdAt: Long,
        kind: Int,
        tags: List<List<String>>,
        content: String,
    ): String {
        val array = buildJsonArray {
            add(JsonPrimitive(0))
            add(JsonPrimitive(pubkey))
            add(JsonPrimitive(createdAt))
            add(JsonPrimitive(kind))
            add(buildJsonArray {
                for (tag in tags) {
                    add(buildJsonArray {
                        for (item in tag) {
                            add(JsonPrimitive(item))
                        }
                    })
                }
            })
            add(JsonPrimitive(content))
        }
        return json.encodeToString(array)
    }

    /**
     * Compute SHA-256 hash and return as hex string.
     * Uses a simple implementation that works across platforms.
     */
    fun sha256Hex(data: ByteArray): String {
        return Hex.encode(sha256(data))
    }

    /**
     * Platform-independent SHA-256 using a pure Kotlin implementation.
     */
    fun sha256(data: ByteArray): ByteArray {
        return Sha256.digest(data)
    }

    /**
     * Parse relay message from JSON text.
     * Relay messages are JSON arrays: ["TYPE", ...]
     */
    fun parseRelayMessage(text: String): RelayMessage {
        val array = json.parseToJsonElement(text).jsonArray
        val type = array[0].jsonPrimitive.content

        return when (type) {
            "EVENT" -> {
                val subscriptionId = array[1].jsonPrimitive.content
                val event = json.decodeFromJsonElement(NostrEvent.serializer(), array[2])
                RelayMessage.EventMsg(subscriptionId, event)
            }
            "OK" -> {
                val eventId = array[1].jsonPrimitive.content
                val success = array[2].jsonPrimitive.content.toBoolean()
                val message = if (array.size > 3) array[3].jsonPrimitive.content else ""
                RelayMessage.OkMsg(eventId, success, message)
            }
            "EOSE" -> {
                val subscriptionId = array[1].jsonPrimitive.content
                RelayMessage.EoseMsg(subscriptionId)
            }
            "CLOSED" -> {
                val subscriptionId = array[1].jsonPrimitive.content
                val message = if (array.size > 2) array[2].jsonPrimitive.content else ""
                RelayMessage.ClosedMsg(subscriptionId, message)
            }
            "NOTICE" -> {
                val message = array[1].jsonPrimitive.content
                RelayMessage.NoticeMsg(message)
            }
            "AUTH" -> {
                val challenge = array[1].jsonPrimitive.content
                RelayMessage.AuthMsg(challenge)
            }
            else -> throw NostrException("Unknown relay message type: $type")
        }
    }

    /**
     * Build a client EVENT message: ["EVENT", event]
     */
    fun buildEventMessage(event: NostrEvent): String {
        val eventJson = json.encodeToString(event)
        return """["EVENT",$eventJson]"""
    }

    /**
     * Build a client REQ message: ["REQ", subscription_id, filter1, filter2, ...]
     */
    fun buildReqMessage(subscriptionId: String, filters: List<NostrFilter>): String {
        val sb = StringBuilder()
        sb.append("""["REQ","$subscriptionId"""")
        for (filter in filters) {
            sb.append(",")
            sb.append(json.encodeToString(filter))
        }
        sb.append("]")
        return sb.toString()
    }

    /**
     * Build a client CLOSE message: ["CLOSE", subscription_id]
     */
    fun buildCloseMessage(subscriptionId: String): String {
        return """["CLOSE","$subscriptionId"]"""
    }

    /**
     * Build a client AUTH message: ["AUTH", signedEvent] (NIP-42)
     */
    fun buildAuthMessage(event: NostrEvent): String {
        val eventJson = json.encodeToString(event)
        return """["AUTH",$eventJson]"""
    }
}
