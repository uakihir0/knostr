package work.socialhub.knostr

import work.socialhub.knostr.internal.InternalUtility
import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {

    @Test
    fun testEmptyString() {
        val hash = InternalUtility.sha256Hex("".encodeToByteArray())
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash)
    }

    @Test
    fun testAbc() {
        val hash = InternalUtility.sha256Hex("abc".encodeToByteArray())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash)
    }

    @Test
    fun testLongString() {
        val input = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"
        val hash = InternalUtility.sha256Hex(input.encodeToByteArray())
        assertEquals("248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1", hash)
    }
}
