package work.socialhub.knostr.cipher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Secp256k1Test {

    private fun hexToBytes(hex: String): ByteArray {
        val bytes = ByteArray(hex.length / 2)
        for (i in bytes.indices) {
            bytes[i] = ((hexDigit(hex[i * 2]) shl 4) or hexDigit(hex[i * 2 + 1])).toByte()
        }
        return bytes
    }

    private fun hexDigit(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> error("Invalid hex: $c")
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(hexChars[v ushr 4])
            sb.append(hexChars[v and 0x0F])
        }
        return sb.toString()
    }

    // --- pubkeyCreate tests ---

    @Test
    fun testPubkeyCreate() {
        val privKey = hexToBytes("67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa")
        val pubKey = Secp256k1.pubkeyCreate(privKey)
        assertEquals(33, pubKey.size)
        val xOnly = pubKey.drop(1).toByteArray()
        assertEquals(32, xOnly.size)
    }

    @Test
    fun testPubkeyCreateKnownVector() {
        // BIP-340 test vector 0: secret key 3
        val privKey = hexToBytes("0000000000000000000000000000000000000000000000000000000000000003")
        val pubKey = Secp256k1.pubkeyCreate(privKey)
        val xOnly = bytesToHex(pubKey.drop(1).toByteArray())
        assertEquals("f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9", xOnly)
    }

    // --- BIP-340 Schnorr signing tests (official test vectors) ---

    @Test
    fun testSignSchnorrVector0() {
        val secKey = hexToBytes("0000000000000000000000000000000000000000000000000000000000000003")
        val msg = hexToBytes("0000000000000000000000000000000000000000000000000000000000000000")
        val auxRand = hexToBytes("0000000000000000000000000000000000000000000000000000000000000000")

        val sig = Secp256k1.signSchnorr(msg, secKey, auxRand)
        assertEquals(64, sig.size)

        val expectedSig = "e907831f80848d1069a5371b402410364bdf1c5f8307b0084c55f1ce2dca821525f66a4a85ea8b71e482a74f382d2ce5ebeee8fdb2172f477df4900d310536c0"
        assertEquals(expectedSig, bytesToHex(sig))
    }

    @Test
    fun testSignSchnorrVector1() {
        val secKey = hexToBytes("b7e151628aed2a6abf7158809cf4f3c762e7160f38b4da56a784d9045190cfef")
        val msg = hexToBytes("243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89")
        val auxRand = hexToBytes("0000000000000000000000000000000000000000000000000000000000000001")

        val sig = Secp256k1.signSchnorr(msg, secKey, auxRand)
        assertEquals(64, sig.size)

        val expectedSig = "6896bd60eeae296db48a229ff71dfe071bde413e6d43f917dc8dcf8c78de33418906d11ac976abccb20b091292bff4ea897efcb639ea871cfa95f6de339e4b0a"
        assertEquals(expectedSig, bytesToHex(sig))
    }

    @Test
    fun testSignSchnorrVector2() {
        val secKey = hexToBytes("c90fdaa22168c234c4c6628b80dc1cd129024e088a67cc74020bbea63b14e5c9")
        val msg = hexToBytes("7e2d58d8b3bcdf1abadec7829054f90dda9805aab56c77333024b9d0a508b75c")
        val auxRand = hexToBytes("c87aa53824b4d7ae2eb035a2b5bbbccc080e76cdc6d1692c4b0b62d798e6d906")

        val sig = Secp256k1.signSchnorr(msg, secKey, auxRand)
        assertEquals(64, sig.size)

        val expectedSig = "5831aaeed7b44bb74e5eab94ba9d4294c49bcf2a60728d8b4c200f50dd313c1bab745879a5ad954a72c45a91c3a51d3c7adea98d82f8481e0e1e03674a6f3fb7"
        assertEquals(expectedSig, bytesToHex(sig))
    }

    @Test
    fun testSignSchnorrVector3() {
        val secKey = hexToBytes("0b432b2677937381aef05bb02a66ecd012773062cf3fa2549e44f58ed2401710")
        val msg = hexToBytes("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
        val auxRand = hexToBytes("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")

        val sig = Secp256k1.signSchnorr(msg, secKey, auxRand)
        assertEquals(64, sig.size)

        val expectedSig = "7eb0509757e246f19449885651611cb965ecc1a187dd51b64fda1edc9637d5ec97582b9cb13db3933705b32ba982af5af25fd78881ebb32771fc5922efc66ea3"
        assertEquals(expectedSig, bytesToHex(sig))
    }

    // --- BIP-340 Schnorr verification tests (official test vectors) ---

    @Test
    fun testVerifyVector0() {
        val pubKey = hexToBytes("f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9")
        val msg = hexToBytes("0000000000000000000000000000000000000000000000000000000000000000")
        val sig = hexToBytes("e907831f80848d1069a5371b402410364bdf1c5f8307b0084c55f1ce2dca821525f66a4a85ea8b71e482a74f382d2ce5ebeee8fdb2172f477df4900d310536c0")
        assertTrue(Secp256k1.verifySchnorr(sig, msg, pubKey))
    }

    @Test
    fun testVerifyVector1() {
        val pubKey = hexToBytes("dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659")
        val msg = hexToBytes("243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89")
        val sig = hexToBytes("6896bd60eeae296db48a229ff71dfe071bde413e6d43f917dc8dcf8c78de33418906d11ac976abccb20b091292bff4ea897efcb639ea871cfa95f6de339e4b0a")
        assertTrue(Secp256k1.verifySchnorr(sig, msg, pubKey))
    }

    @Test
    fun testVerifyVector2() {
        val pubKey = hexToBytes("dd308afec5777e13121fa72b9cc1b7cc0139715309b086c960e18fd969774eb8")
        val msg = hexToBytes("7e2d58d8b3bcdf1abadec7829054f90dda9805aab56c77333024b9d0a508b75c")
        val sig = hexToBytes("5831aaeed7b44bb74e5eab94ba9d4294c49bcf2a60728d8b4c200f50dd313c1bab745879a5ad954a72c45a91c3a51d3c7adea98d82f8481e0e1e03674a6f3fb7")
        assertTrue(Secp256k1.verifySchnorr(sig, msg, pubKey))
    }

    @Test
    fun testVerifyVector3() {
        val pubKey = hexToBytes("25d1dff95105f5253c4022f628a996ad3a0d95fbf21d468a1b33f8c160d8f517")
        val msg = hexToBytes("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
        val sig = hexToBytes("7eb0509757e246f19449885651611cb965ecc1a187dd51b64fda1edc9637d5ec97582b9cb13db3933705b32ba982af5af25fd78881ebb32771fc5922efc66ea3")
        assertTrue(Secp256k1.verifySchnorr(sig, msg, pubKey))
    }

    // --- Verification tests (positive and negative, from BIP-340 CSV) ---

    @Test
    fun testVerifyVector4_true() {
        // Vector 4: TRUE, no secret key
        val pubKey = hexToBytes("d69c3509bb99e412e68b0fe8544e72837dfa30746d8be2aa65975f29d22dc7b9")
        val msg = hexToBytes("4df3c3f68fcc83b27e9d42c90431a72499f17875c81a599b566c9889b9696703")
        val sig = hexToBytes("00000000000000000000003b78ce563f89a0ed9414f5aa28ad0d96d6795f9c6376afb1548af603b3eb45c9f8207dee1060cb71c04e80f593060b07d28308d7f4")
        assertTrue(Secp256k1.verifySchnorr(sig, msg, pubKey))
    }

    @Test
    fun testVerifyVector5_publicKeyNotOnCurve() {
        val pubKey = hexToBytes("eefdea4cdb677750a420fee807eacf21eb9898ae79b9768766e4faa04a2d4a34")
        val msg = hexToBytes("243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89")
        val sig = hexToBytes("6cff5c3ba86c69ea4b7376f31a9bcb4f74c1976089b2d9963da2e5543e17776969e89b4c5564d00349106b8497785dd7d1d713a8ae82b32fa79d5f7fc407d39b")
        assertFalse(Secp256k1.verifySchnorr(sig, msg, pubKey))
    }

    @Test
    fun testVerifyVector6_hasOddY() {
        val pubKey = hexToBytes("dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659")
        val msg = hexToBytes("243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89")
        val sig = hexToBytes("fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a14602975563cc27944640ac607cd107ae10923d9ef7a73c643e166be5ebeafa34b1ac553e2")
        assertFalse(Secp256k1.verifySchnorr(sig, msg, pubKey))
    }

    @Test
    fun testVerifyVector7_negatedMsg() {
        val pubKey = hexToBytes("dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659")
        val msg = hexToBytes("243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89")
        val sig = hexToBytes("1fa62e331edbc21c394792d2ab1100a7b432b013df3f6ff4f99fcb33e0e1515f28890b3edb6e7189b630448b515ce4f8622a954cfe545735aaea5134fccdb2bd")
        assertFalse(Secp256k1.verifySchnorr(sig, msg, pubKey))
    }

    @Test
    fun testVerifyVector8_negatedS() {
        val pubKey = hexToBytes("dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659")
        val msg = hexToBytes("243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89")
        val sig = hexToBytes("6cff5c3ba86c69ea4b7376f31a9bcb4f74c1976089b2d9963da2e5543e177769961764b3aa9b2ffcb6ef947b6887a226e8d7c93e00c5ed0c1834ff0d0c2e6da6")
        assertFalse(Secp256k1.verifySchnorr(sig, msg, pubKey))
    }

    // --- Sign then verify round-trip ---

    @Test
    fun testSignThenVerify() {
        val privKey = hexToBytes("67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa")
        val pubKey = Secp256k1.pubkeyCreate(privKey).drop(1).toByteArray()
        val msg = hexToBytes("7e2d58d8b3bcdf1abadec7829054f90dda9805aab56c77333024b9d0a508b75c")

        val sig = Secp256k1.signSchnorr(msg, privKey, null)
        assertEquals(64, sig.size)
        assertTrue(Secp256k1.verifySchnorr(sig, msg, pubKey))
    }

    @Test
    fun testSignThenVerifyWithAuxRand() {
        val privKey = hexToBytes("67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa")
        val pubKey = Secp256k1.pubkeyCreate(privKey).drop(1).toByteArray()
        val msg = hexToBytes("0000000000000000000000000000000000000000000000000000000000000000")
        val auxRand = hexToBytes("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")

        val sig = Secp256k1.signSchnorr(msg, privKey, auxRand)
        assertTrue(Secp256k1.verifySchnorr(sig, msg, pubKey))
    }

    // --- ECDH shared secret tests ---

    @Test
    fun testComputeSharedSecret() {
        // ECDH: alice_priv * bob_pub == bob_priv * alice_pub
        val alicePriv = hexToBytes("67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa")
        val bobPriv = hexToBytes("b7e151628aed2a6abf7158809cf4f3c762e7160f38b4da56a784d9045190cfef")

        val alicePub = Secp256k1.pubkeyCreate(alicePriv).drop(1).toByteArray()
        val bobPub = Secp256k1.pubkeyCreate(bobPriv).drop(1).toByteArray()

        val sharedAlice = Secp256k1.computeSharedSecret(alicePriv, bobPub)
        val sharedBob = Secp256k1.computeSharedSecret(bobPriv, alicePub)

        assertEquals(32, sharedAlice.size)
        assertEquals(32, sharedBob.size)
        assertEquals(bytesToHex(sharedAlice), bytesToHex(sharedBob))
    }

    @Test
    fun testComputeSharedSecretDeterministic() {
        val privKey = hexToBytes("0000000000000000000000000000000000000000000000000000000000000003")
        val pubKey = hexToBytes("f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9")

        val shared1 = Secp256k1.computeSharedSecret(privKey, pubKey)
        val shared2 = Secp256k1.computeSharedSecret(privKey, pubKey)

        assertEquals(bytesToHex(shared1), bytesToHex(shared2))
    }
}
