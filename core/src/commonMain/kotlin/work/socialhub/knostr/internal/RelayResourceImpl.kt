package work.socialhub.knostr.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import work.socialhub.knostr.NostrConfig
import work.socialhub.knostr.api.RelayResource
import work.socialhub.knostr.relay.RelayPool
import work.socialhub.knostr.util.toBlocking

class RelayResourceImpl(
    private val config: NostrConfig,
    private val relayPool: RelayPool,
) : RelayResource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun connect() {
        // Add relays from config if not already added
        for (url in config.relayUrls) {
            if (url !in relayPool.getConnectedRelays()) {
                relayPool.addRelay(url, config)
            }
        }
        relayPool.connectAll(scope)
    }

    override suspend fun disconnect() {
        relayPool.disconnectAll()
    }

    override fun getConnectedRelays(): List<String> {
        return relayPool.getConnectedRelays()
    }

    override fun connectBlocking() {
        toBlocking { connect() }
    }

    override fun disconnectBlocking() {
        toBlocking { disconnect() }
    }
}
