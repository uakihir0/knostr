package work.socialhub.knostr.social

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrFactory
import work.socialhub.knostr.signing.createSigner
import java.io.File
import kotlin.test.BeforeTest

open class AbstractTest {

    companion object {
        var PRIVATE_KEY: String? = null
        var RELAYS: List<String>? = null
    }

    protected val json = Json {
        ignoreUnknownKeys = true
    }

    fun nostr(): Nostr {
        return NostrFactory.instance(PRIVATE_KEY!!, RELAYS!!)
    }

    fun social(): NostrSocial {
        return NostrSocialFactory.instance(nostr())
    }

    fun publicKey(): String {
        return createSigner(PRIVATE_KEY!!).getPublicKey()
    }

    /**
     * Connect relays with a background scope (non-blocking).
     * Returns a scope that should be cancelled after tests.
     */
    suspend fun connectRelays(nostr: Nostr): CoroutineScope {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val config = nostr.config()
        for (url in config.relayUrls) {
            nostr.relayPool().addRelay(url, config)
        }
        nostr.relayPool().connectAll(scope)
        delay(2000)
        return scope
    }

    fun disconnectRelays(nostr: Nostr, scope: CoroutineScope) {
        nostr.relayPool().disconnectAll()
        scope.cancel()
    }

    @BeforeTest
    fun setupTest() {

        try {
            // Get credentials from environment variables.
            PRIVATE_KEY = System.getenv("NOSTR_PRIVATE_KEY")
                ?: System.getProperty("NOSTR_PRIVATE_KEY")

            val relaysStr = System.getenv("NOSTR_RELAYS")
                ?: System.getProperty("NOSTR_RELAYS")
            if (!relaysStr.isNullOrEmpty()) {
                RELAYS = relaysStr.split(",").map { it.trim() }
            }
        } catch (_: Exception) {
        }

        if (PRIVATE_KEY == null || RELAYS == null) {
            try {
                // Get credentials from secrets.json file.
                readTestProps()?.get("nostr")?.let {
                    PRIVATE_KEY = it["NOSTR_PRIVATE_KEY"]

                    val relaysStr = it["NOSTR_RELAYS"]
                    if (!relaysStr.isNullOrEmpty()) {
                        RELAYS = relaysStr.split(",").map { r -> r.trim() }
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (PRIVATE_KEY == null || RELAYS == null) {
            throw IllegalStateException(
                """!!!
                No credentials exist for testing.
                Set the environment variables NOSTR_PRIVATE_KEY and NOSTR_RELAYS
                or copy the following file and describe its contents.
                `cp secrets.json.default secrets.json`
                !!!""".trimIndent()
            )
        }
    }

    /**
     * Read Test Properties
     */
    private fun readTestProps(): Map<String, Map<String, String>>? {
        return try {
            val jsonStr = File("../secrets.json").readText()
            json.decodeFromString<Map<String, Map<String, String>>>(jsonStr)
        } catch (e: Exception) {
            null
        }
    }
}
