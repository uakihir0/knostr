package work.socialhub.knostr.cipher

/**
 * Pure Kotlin AES-256 block cipher (ECB core).
 * Implements the FIPS 197 AES specification for 256-bit keys (14 rounds).
 */
object Aes {

    private const val BLOCK_SIZE = 16
    private const val NK = 8    // Key length in 32-bit words (256/32)
    private const val NR = 14   // Number of rounds for AES-256

    /**
     * Encrypt a single 16-byte block using AES-256.
     */
    fun encryptBlock(input: ByteArray, key: ByteArray): ByteArray {
        require(input.size == BLOCK_SIZE) { "Input must be $BLOCK_SIZE bytes" }
        require(key.size == 32) { "Key must be 32 bytes" }

        val roundKeys = keyExpansion(key)
        val state = Array(4) { r -> IntArray(4) { c -> input[c * 4 + r].toInt() and 0xFF } }

        // Initial round key addition
        addRoundKey(state, roundKeys, 0)

        // Rounds 1 to NR-1
        for (round in 1 until NR) {
            subBytes(state)
            shiftRows(state)
            mixColumns(state)
            addRoundKey(state, roundKeys, round)
        }

        // Final round (no MixColumns)
        subBytes(state)
        shiftRows(state)
        addRoundKey(state, roundKeys, NR)

        // Convert state back to byte array
        val output = ByteArray(BLOCK_SIZE)
        for (c in 0 until 4) {
            for (r in 0 until 4) {
                output[c * 4 + r] = state[r][c].toByte()
            }
        }
        return output
    }

    /**
     * Decrypt a single 16-byte block using AES-256.
     */
    fun decryptBlock(input: ByteArray, key: ByteArray): ByteArray {
        require(input.size == BLOCK_SIZE) { "Input must be $BLOCK_SIZE bytes" }
        require(key.size == 32) { "Key must be 32 bytes" }

        val roundKeys = keyExpansion(key)
        val state = Array(4) { r -> IntArray(4) { c -> input[c * 4 + r].toInt() and 0xFF } }

        // Initial round key addition (last round key)
        addRoundKey(state, roundKeys, NR)

        // Rounds NR-1 down to 1
        for (round in NR - 1 downTo 1) {
            invShiftRows(state)
            invSubBytes(state)
            addRoundKey(state, roundKeys, round)
            invMixColumns(state)
        }

        // Final round (no InvMixColumns)
        invShiftRows(state)
        invSubBytes(state)
        addRoundKey(state, roundKeys, 0)

        val output = ByteArray(BLOCK_SIZE)
        for (c in 0 until 4) {
            for (r in 0 until 4) {
                output[c * 4 + r] = state[r][c].toByte()
            }
        }
        return output
    }

    // --- Key Expansion ---

    private fun keyExpansion(key: ByteArray): Array<IntArray> {
        val w = IntArray(4 * (NR + 1))

        // Copy key into first NK words
        for (i in 0 until NK) {
            w[i] = ((key[4 * i].toInt() and 0xFF) shl 24) or
                ((key[4 * i + 1].toInt() and 0xFF) shl 16) or
                ((key[4 * i + 2].toInt() and 0xFF) shl 8) or
                (key[4 * i + 3].toInt() and 0xFF)
        }

        for (i in NK until 4 * (NR + 1)) {
            var temp = w[i - 1]
            if (i % NK == 0) {
                temp = subWord(rotWord(temp)) xor RCON[i / NK]
            } else if (i % NK == 4) {
                temp = subWord(temp)
            }
            w[i] = w[i - NK] xor temp
        }

        // Convert to round keys (4 words per round)
        return Array(NR + 1) { round ->
            IntArray(4) { col -> w[round * 4 + col] }
        }
    }

    private fun subWord(word: Int): Int {
        return (SBOX[(word ushr 24) and 0xFF] shl 24) or
            (SBOX[(word ushr 16) and 0xFF] shl 16) or
            (SBOX[(word ushr 8) and 0xFF] shl 8) or
            SBOX[word and 0xFF]
    }

    private fun rotWord(word: Int): Int {
        return (word shl 8) or (word ushr 24)
    }

    // --- State Operations ---

    private fun addRoundKey(state: Array<IntArray>, roundKeys: Array<IntArray>, round: Int) {
        val rk = roundKeys[round]
        for (c in 0 until 4) {
            state[0][c] = state[0][c] xor ((rk[c] ushr 24) and 0xFF)
            state[1][c] = state[1][c] xor ((rk[c] ushr 16) and 0xFF)
            state[2][c] = state[2][c] xor ((rk[c] ushr 8) and 0xFF)
            state[3][c] = state[3][c] xor (rk[c] and 0xFF)
        }
    }

    private fun subBytes(state: Array<IntArray>) {
        for (r in 0 until 4) for (c in 0 until 4) {
            state[r][c] = SBOX[state[r][c]]
        }
    }

    private fun invSubBytes(state: Array<IntArray>) {
        for (r in 0 until 4) for (c in 0 until 4) {
            state[r][c] = INV_SBOX[state[r][c]]
        }
    }

    private fun shiftRows(state: Array<IntArray>) {
        // Row 0: no shift
        // Row 1: shift left 1
        val t1 = state[1][0]; state[1][0] = state[1][1]; state[1][1] = state[1][2]; state[1][2] = state[1][3]; state[1][3] = t1
        // Row 2: shift left 2
        var t = state[2][0]; state[2][0] = state[2][2]; state[2][2] = t; t = state[2][1]; state[2][1] = state[2][3]; state[2][3] = t
        // Row 3: shift left 3 (= shift right 1)
        val t3 = state[3][3]; state[3][3] = state[3][2]; state[3][2] = state[3][1]; state[3][1] = state[3][0]; state[3][0] = t3
    }

    private fun invShiftRows(state: Array<IntArray>) {
        // Row 1: shift right 1
        val t1 = state[1][3]; state[1][3] = state[1][2]; state[1][2] = state[1][1]; state[1][1] = state[1][0]; state[1][0] = t1
        // Row 2: shift right 2
        var t = state[2][0]; state[2][0] = state[2][2]; state[2][2] = t; t = state[2][1]; state[2][1] = state[2][3]; state[2][3] = t
        // Row 3: shift right 3 (= shift left 1)
        val t3 = state[3][0]; state[3][0] = state[3][1]; state[3][1] = state[3][2]; state[3][2] = state[3][3]; state[3][3] = t3
    }

    private fun mixColumns(state: Array<IntArray>) {
        for (c in 0 until 4) {
            val s0 = state[0][c]; val s1 = state[1][c]; val s2 = state[2][c]; val s3 = state[3][c]
            state[0][c] = gmul(2, s0) xor gmul(3, s1) xor s2 xor s3
            state[1][c] = s0 xor gmul(2, s1) xor gmul(3, s2) xor s3
            state[2][c] = s0 xor s1 xor gmul(2, s2) xor gmul(3, s3)
            state[3][c] = gmul(3, s0) xor s1 xor s2 xor gmul(2, s3)
        }
    }

    private fun invMixColumns(state: Array<IntArray>) {
        for (c in 0 until 4) {
            val s0 = state[0][c]; val s1 = state[1][c]; val s2 = state[2][c]; val s3 = state[3][c]
            state[0][c] = gmul(14, s0) xor gmul(11, s1) xor gmul(13, s2) xor gmul(9, s3)
            state[1][c] = gmul(9, s0) xor gmul(14, s1) xor gmul(11, s2) xor gmul(13, s3)
            state[2][c] = gmul(13, s0) xor gmul(9, s1) xor gmul(14, s2) xor gmul(11, s3)
            state[3][c] = gmul(11, s0) xor gmul(13, s1) xor gmul(9, s2) xor gmul(14, s3)
        }
    }

    /**
     * Galois field multiplication in GF(2^8) with irreducible polynomial x^8 + x^4 + x^3 + x + 1.
     */
    private fun gmul(a: Int, b: Int): Int {
        var result = 0
        var aa = a
        var bb = b
        for (i in 0 until 8) {
            if (bb and 1 != 0) result = result xor aa
            val hi = aa and 0x80
            aa = (aa shl 1) and 0xFF
            if (hi != 0) aa = aa xor 0x1B
            bb = bb shr 1
        }
        return result
    }

    // --- AES S-Box ---

    private val SBOX = intArrayOf(
        0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
        0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
        0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
        0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
        0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
        0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
        0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
        0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
        0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
        0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
        0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
        0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
        0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
        0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
        0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
        0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16,
    )

    private val INV_SBOX = intArrayOf(
        0x52, 0x09, 0x6a, 0xd5, 0x30, 0x36, 0xa5, 0x38, 0xbf, 0x40, 0xa3, 0x9e, 0x81, 0xf3, 0xd7, 0xfb,
        0x7c, 0xe3, 0x39, 0x82, 0x9b, 0x2f, 0xff, 0x87, 0x34, 0x8e, 0x43, 0x44, 0xc4, 0xde, 0xe9, 0xcb,
        0x54, 0x7b, 0x94, 0x32, 0xa6, 0xc2, 0x23, 0x3d, 0xee, 0x4c, 0x95, 0x0b, 0x42, 0xfa, 0xc3, 0x4e,
        0x08, 0x2e, 0xa1, 0x66, 0x28, 0xd9, 0x24, 0xb2, 0x76, 0x5b, 0xa2, 0x49, 0x6d, 0x8b, 0xd1, 0x25,
        0x72, 0xf8, 0xf6, 0x64, 0x86, 0x68, 0x98, 0x16, 0xd4, 0xa4, 0x5c, 0xcc, 0x5d, 0x65, 0xb6, 0x92,
        0x6c, 0x70, 0x48, 0x50, 0xfd, 0xed, 0xb9, 0xda, 0x5e, 0x15, 0x46, 0x57, 0xa7, 0x8d, 0x9d, 0x84,
        0x90, 0xd8, 0xab, 0x00, 0x8c, 0xbc, 0xd3, 0x0a, 0xf7, 0xe4, 0x58, 0x05, 0xb8, 0xb3, 0x45, 0x06,
        0xd0, 0x2c, 0x1e, 0x8f, 0xca, 0x3f, 0x0f, 0x02, 0xc1, 0xaf, 0xbd, 0x03, 0x01, 0x13, 0x8a, 0x6b,
        0x3a, 0x91, 0x11, 0x41, 0x4f, 0x67, 0xdc, 0xea, 0x97, 0xf2, 0xcf, 0xce, 0xf0, 0xb4, 0xe6, 0x73,
        0x96, 0xac, 0x74, 0x22, 0xe7, 0xad, 0x35, 0x85, 0xe2, 0xf9, 0x37, 0xe8, 0x1c, 0x75, 0xdf, 0x6e,
        0x47, 0xf1, 0x1a, 0x71, 0x1d, 0x29, 0xc5, 0x89, 0x6f, 0xb7, 0x62, 0x0e, 0xaa, 0x18, 0xbe, 0x1b,
        0xfc, 0x56, 0x3e, 0x4b, 0xc6, 0xd2, 0x79, 0x20, 0x9a, 0xdb, 0xc0, 0xfe, 0x78, 0xcd, 0x5a, 0xf4,
        0x1f, 0xdd, 0xa8, 0x33, 0x88, 0x07, 0xc7, 0x31, 0xb1, 0x12, 0x10, 0x59, 0x27, 0x80, 0xec, 0x5f,
        0x60, 0x51, 0x7f, 0xa9, 0x19, 0xb5, 0x4a, 0x0d, 0x2d, 0xe5, 0x7a, 0x9f, 0x93, 0xc9, 0x9c, 0xef,
        0xa0, 0xe0, 0x3b, 0x4d, 0xae, 0x2a, 0xf5, 0xb0, 0xc8, 0xeb, 0xbb, 0x3c, 0x83, 0x53, 0x99, 0x61,
        0x17, 0x2b, 0x04, 0x7e, 0xba, 0x77, 0xd6, 0x26, 0xe1, 0x69, 0x14, 0x63, 0x55, 0x21, 0x0c, 0x7d,
    )

    private val RCON = intArrayOf(
        0x00000000.toInt(),
        0x01000000, 0x02000000, 0x04000000, 0x08000000,
        0x10000000, 0x20000000, 0x40000000, 0x80000000.toInt(),
        0x1B000000, 0x36000000, 0x6C000000, 0xD8000000.toInt(),
        0xAB000000.toInt(), 0x4D000000,
    )
}
