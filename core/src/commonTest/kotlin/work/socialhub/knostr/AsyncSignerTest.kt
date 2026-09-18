package work.socialhub.knostr

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.internal.EventResourceImpl
import work.socialhub.knostr.relay.RelayPool
import work.socialhub.knostr.signing.NostrSigner
import work.socialhub.knostr.signing.createSigner

/**
 * The SDK must be able to drive a signer whose key material is only reachable
 * asynchronously (a NIP-07 extension, a NIP-46 bunker). These tests pin that
 * contract: the default `*Async` methods delegate to the synchronous ones, and
 * the event-signing paths call the async variants exclusively.
 */
class AsyncSignerTest {

    private val publicKey = "a".repeat(64)

    private class AsyncOnlySigner(private val publicKey: String) : NostrSigner {
        val signedEvents = mutableListOf<UnsignedEvent>()
        var publicKeyRequests = 0

        override fun getPublicKey(): String =
            unsupported("getPublicKey")

        override fun sign(event: UnsignedEvent): NostrEvent =
            unsupported("sign")

        override fun computeEventId(event: UnsignedEvent): String =
            unsupported("computeEventId")

        override fun nip44Encrypt(plaintext: String, recipientPubkey: String): String =
            unsupported("nip44Encrypt")

        override fun nip44Decrypt(payload: String, senderPubkey: String): String =
            unsupported("nip44Decrypt")

        override fun nip04Encrypt(plaintext: String, recipientPubkey: String): String =
            unsupported("nip04Encrypt")

        override fun nip04Decrypt(ciphertext: String, senderPubkey: String): String =
            unsupported("nip04Decrypt")

        override suspend fun getPublicKeyAsync(): String {
            publicKeyRequests += 1
            return publicKey
        }

        override suspend fun signAsync(event: UnsignedEvent): NostrEvent {
            signedEvents += event
            return NostrEvent(
                id = "id-${signedEvents.size}",
                pubkey = publicKey,
                createdAt = event.createdAt,
                kind = event.kind,
                tags = event.tags,
                content = event.content,
                sig = "b".repeat(128),
            )
        }

        private fun unsupported(name: String): Nothing =
            throw UnsupportedOperationException("$name must not be called on an async-only signer")
    }

    @Test
    fun secp256k1SignerInheritsAsyncDefaults() = runTest {
        val signer = createSigner("11".repeat(32))
        val pubkey = signer.getPublicKey()
        val unsigned = UnsignedEvent(
            pubkey = pubkey,
            createdAt = 1_700_000_000,
            kind = EventKind.TEXT_NOTE,
            content = "hello",
        )

        assertEquals(pubkey, signer.getPublicKeyAsync())

        // Schnorr signatures carry random auxiliary randomness, so compare the
        // deterministic event id rather than the signature bytes.
        val syncSigned = signer.sign(unsigned)
        val asyncSigned = signer.signAsync(unsigned)
        assertEquals(syncSigned.id, asyncSigned.id)
        assertEquals(syncSigned.pubkey, asyncSigned.pubkey)
        assertEquals(128, asyncSigned.sig.length)

        // NIP-44 ciphertexts are randomized too; the async default must be
        // readable through the synchronous decrypt path.
        val encrypted = signer.nip44EncryptAsync("payload", pubkey)
        assertEquals("payload", signer.nip44Decrypt(encrypted, pubkey))
    }

    @Test
    fun deleteEventSignsWithAsyncOnlySigner() = runTest {
        val signer = AsyncOnlySigner(publicKey)
        val config = NostrConfig().also { it.signer = signer }

        val response = EventResourceImpl(config, RelayPool())
            .deleteEvent("e".repeat(64), "bye")

        assertTrue(response.data)
        val unsigned = signer.signedEvents.single()
        assertEquals(EventKind.EVENT_DELETION, unsigned.kind)
        assertEquals(publicKey, unsigned.pubkey)
        assertEquals(listOf(listOf("e", "e".repeat(64))), unsigned.tags)
        assertEquals("bye", unsigned.content)
        assertTrue(signer.publicKeyRequests >= 1)
    }

    @Test
    fun relayPoolBuildsAuthEventWithAsyncOnlySigner() = runTest {
        val signer = AsyncOnlySigner(publicKey)
        val pool = RelayPool().also { it.signer = signer }

        val auth = assertNotNull(pool.createAuthEvent("wss://relay.example.com", "challenge-1"))

        assertEquals(EventKind.AUTH, auth.kind)
        assertEquals(publicKey, auth.pubkey)
        assertEquals(
            listOf(
                listOf("relay", "wss://relay.example.com"),
                listOf("challenge", "challenge-1"),
            ),
            auth.tags,
        )
        assertEquals("", auth.content)
        assertEquals(1, signer.signedEvents.size)
    }

    @Test
    fun relayPoolWithoutSignerSkipsAuth() = runTest {
        val pool = RelayPool()
        assertNull(pool.createAuthEvent("wss://relay.example.com", "challenge-1"))
    }
}
