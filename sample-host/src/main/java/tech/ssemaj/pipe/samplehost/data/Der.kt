package tech.ssemaj.pipe.samplehost.data

/** Just enough DER to walk the Android key-attestation KeyDescription sequence. Not general-purpose. */
internal object

Der {
    internal data class Tlv(val tag: Int, val value: ByteArray, val end: Int)

    internal fun parse(bytes: ByteArray, offset: Int): Tlv {
        val tag = bytes[offset].toInt() and 0xFF
        var i = offset + 1
        var len = bytes[i].toInt() and 0xFF
        i++
        if (len and 0x80 != 0) {
            val n = len and 0x7F
            require(n in 1..4) { "unsupported DER length-of-length: $n" }
            len = 0
            repeat(n) {
                len = (len shl 8) or (bytes[i].toInt() and 0xFF)
                i++
            }
        }
        require(i + len <= bytes.size) { "DER length overruns buffer" }
        return Tlv(tag, bytes.copyOfRange(i, i + len), i + len)
    }

    internal fun children(sequenceValue: ByteArray): List<Tlv> {
        val out = mutableListOf<Tlv>()
        var i = 0
        while (i < sequenceValue.size) {
            val tlv = parse(sequenceValue, i)
            out.add(tlv)
            i = tlv.end
        }
        return out
    }
}
