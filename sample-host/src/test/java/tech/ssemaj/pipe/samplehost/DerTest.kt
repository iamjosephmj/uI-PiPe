package tech.ssemaj.pipe.samplehost

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import tech.ssemaj.pipe.samplehost.data.Der

class DerTest {

    @Test fun parsesShortFormLength() {
        // OCTET STRING (0x04), length 3, payload 01 02 03
        val tlv = Der.parse(byteArrayOf(0x04, 0x03, 0x01, 0x02, 0x03), 0)
        assertEquals(0x04, tlv.tag)
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), tlv.value)
        assertEquals(5, tlv.end)
    }

    @Test fun parsesLongFormLength() {
        // OCTET STRING, long-form length 0x81 0x80 = 128 bytes
        val payload = ByteArray(128) { 0x5A }
        val tlv = Der.parse(byteArrayOf(0x04, 0x81.toByte(), 0x80.toByte()) + payload, 0)
        assertEquals(128, tlv.value.size)
        assertEquals(131, tlv.end)
    }

    @Test fun walksSequenceChildren() {
        // SEQUENCE { INTEGER 7, OCTET STRING AB }
        val seq = byteArrayOf(0x02, 0x01, 0x07, 0x04, 0x01, 0xAB.toByte())
        val kids = Der.children(seq)
        assertEquals(2, kids.size)
        assertEquals(0x02, kids[0].tag)
        assertArrayEquals(byteArrayOf(0x07), kids[0].value)
        assertEquals(0x04, kids[1].tag)
        assertArrayEquals(byteArrayOf(0xAB.toByte()), kids[1].value)
    }
}
