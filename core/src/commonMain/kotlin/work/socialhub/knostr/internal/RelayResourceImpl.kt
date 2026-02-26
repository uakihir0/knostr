package work.socialhub.knostr.internal

import work.socialhub.knostr.NostrConfig
import work.socialhub.knostr.api.RelayResource
import work.socialhub.knostr.relay.RelayPool
import work.socialhub.knostr.util.toBlocking

class RelayResourceImpl(
    private val config: NostrConfig,
    private val relayPool: RelayPool,
) : RelayResource {

    override suspend fun connect() {
        // Add relays from config if not already added
        for (url in config.relayUrls) {
            if (url !in relayPool.getConnectedRelays()) {
                relayPool.addRelay(url)
            }
        }
        relayPool.connectAll()
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
