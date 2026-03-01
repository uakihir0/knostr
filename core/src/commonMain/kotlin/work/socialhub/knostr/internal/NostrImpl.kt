package work.socialhub.knostr.internal

import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrConfig
import work.socialhub.knostr.api.EventResource
import work.socialhub.knostr.api.NipResource
import work.socialhub.knostr.api.RelayResource
import work.socialhub.knostr.relay.RelayPool
import work.socialhub.knostr.signing.NostrSigner

class NostrImpl(
    private val config: NostrConfig,
) : Nostr {

    private val pool = RelayPool().also {
        it.signer = config.signer
        it.autoAuth = config.autoAuth
    }
    private val events: EventResource = EventResourceImpl(config, pool)
    private val relays: RelayResource = RelayResourceImpl(config, pool)
    private val nip: NipResource = NipResourceImpl(config)

    override fun events() = events
    override fun relays() = relays
    override fun nip() = nip
    override fun signer() = config.signer
    override fun config() = config
    override fun relayPool() = pool
}
