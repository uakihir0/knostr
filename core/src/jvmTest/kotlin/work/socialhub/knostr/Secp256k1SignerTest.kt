package work.socialhub.knostr

import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.internal.InternalUtility
import work.socialhub.knostr.signing.Secp256k1Signer
import work.socialhub.knostr.util.Hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Secp256k1SignerTest {

    // Well-known test private key (DO NOT use in production)
    private val testPrivateKeyHex = "67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa"

    @Test
    fun testGetPublicKey() {
        val signer = Secp256k1Signer(testPrivateKeyHex)
        val pubkey = signer.getPublicKey()
        // Public key should be valid hex and represent an x-only key
        assertTrue(pubkey.isNotEmpty(), "Public key should not be empty")
        // Should be valid hex
        val bytes = Hex.decode(pubkey)
        assertTrue(bytes.isNotEmpty(), "Public key bytes should not be empty")
    }

    @Test
    fun testComputeEventId() {
        val signer = Secp256k1Signer(testPrivateKeyHex)
        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = 1673347337,
            kind = 1,
            tags = listOf(),
            content = "test content",
        )
        val eventId = signer.computeEventId(unsigned)
        // Event ID should be 64 hex chars (32 bytes SHA-256)
        assertEquals(64, eventId.length)

        // Verify the ID is computed as SHA-256 of the serialized event
        val serialized = InternalUtility.serializeForId(
            pubkey = unsigned.pubkey,
            createdAt = unsigned.createdAt,
            kind = unsigned.kind,
            tags = unsigned.tags,
            content = unsigned.content,
        )
        val expectedId = InternalUtility.sha256Hex(serialized.encodeToByteArray())
        assertEquals(expectedId, eventId)
    }

    @Test
    fun testSign() {
        val signer = Secp256k1Signer(testPrivateKeyHex)
        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = 1673347337,
            kind = 1,
            tags = listOf(),
            content = "hello nostr",
        )
        val signed = signer.sign(unsigned)

        // Signed event should have valid fields
        assertEquals(signer.getPublicKey(), signed.pubkey)
        assertEquals(unsigned.createdAt, signed.createdAt)
        assertEquals(unsigned.kind, signed.kind)
        assertEquals(unsigned.content, signed.content)

        // Signature should be 128 hex chars (64 bytes Schnorr)
        assertEquals(128, signed.sig.length)
        // Should be valid hex
        Hex.decode(signed.sig)

        // Event ID should be 64 hex chars
        assertEquals(64, signed.id.length)
    }
}
