package work.socialhub.knostr.util

/**
 * Bech32/Bech32m encoding/decoding for NIP-19.
 */
object Bech32 {

    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    fun encode(hrp: String, data: ByteArray): String {
        val data5bit = convertBits(data, 8, 5, true)
        val checksum = createChecksum(hrp, data5bit)
        val combined = data5bit + checksum
        val sb = StringBuilder(hrp.length + 1 + combined.size)
        sb.append(hrp)
        sb.append('1')
        for (b in combined) {
            sb.append(CHARSET[b.toInt()])
        }
        return sb.toString()
    }

    fun decode(bech32: String): Pair<String, ByteArray> {
        val lower = bech32.lowercase()
        val pos = lower.lastIndexOf('1')
        require(pos >= 1) { "Invalid bech32 string" }
        val hrp = lower.substring(0, pos)
        val data5bit = ByteArray(lower.length - pos - 1) { i ->
            val c = lower[pos + 1 + i]
            val idx = CHARSET.indexOf(c)
            require(idx >= 0) { "Invalid bech32 character: $c" }
            idx.toByte()
        }
        require(verifyChecksum(hrp, data5bit)) { "Invalid bech32 checksum" }
        val dataWithoutChecksum = data5bit.copyOfRange(0, data5bit.size - 6)
        return hrp to convertBits(dataWithoutChecksum, 5, 8, false)
    }

    private fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): ByteArray {
        var acc = 0
        var bits = 0
        val result = mutableListOf<Byte>()
        val maxv = (1 shl toBits) - 1
        for (b in data) {
            acc = (acc shl fromBits) or (b.toInt() and 0xFF)
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                result.add(((acc shr bits) and maxv).toByte())
            }
        }
        if (pad) {
            if (bits > 0) {
                result.add(((acc shl (toBits - bits)) and maxv).toByte())
            }
        }
        return result.toByteArray()
    }

    private fun polymod(values: ByteArray): Int {
        val gen = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var chk = 1
        for (v in values) {
            val b = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor (v.toInt() and 0xFF)
            for (i in 0 until 5) {
                if ((b ushr i) and 1 == 1) {
                    chk = chk xor gen[i]
                }
            }
        }
        return chk
    }

    private fun hrpExpand(hrp: String): ByteArray {
        val result = ByteArray(hrp.length * 2 + 1)
        for (i in hrp.indices) {
            result[i] = (hrp[i].code ushr 5).toByte()
            result[hrp.length + 1 + i] = (hrp[i].code and 31).toByte()
        }
        result[hrp.length] = 0
        return result
    }

    private fun createChecksum(hrp: String, data: ByteArray): ByteArray {
        val values = hrpExpand(hrp) + data + ByteArray(6)
        val polymod = polymod(values) xor 1
        return ByteArray(6) { i ->
            ((polymod ushr (5 * (5 - i))) and 31).toByte()
        }
    }

    private fun verifyChecksum(hrp: String, data: ByteArray): Boolean {
        return polymod(hrpExpand(hrp) + data) == 1
    }
}
