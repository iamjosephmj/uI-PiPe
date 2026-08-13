package tech.ssemaj.pipe.samplehost

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tech.ssemaj.pipe.samplehost.data.AttestationParser

private fun tlv(tag: Int, value: ByteArray): ByteArray {
    require(value.size < 128)
    return byteArrayOf(tag.toByte(), value.size.toByte()) + value
}

private fun keyDescription(challenge: ByteArray, securityLevel: Int): ByteArray {
    val body = tlv(0x02, byteArrayOf(100)) +             // attestationVersion
        tlv(0x0A, byteArrayOf(securityLevel.toByte())) + // attestationSecurityLevel
        tlv(0x02, byteArrayOf(100)) +                    // keymasterVersion
        tlv(0x0A, byteArrayOf(securityLevel.toByte())) + // keymasterSecurityLevel
        tlv(0x04, challenge) +                           // attestationChallenge
        tlv(0x04, ByteArray(0)) +                        // uniqueId
        tlv(0x30, ByteArray(0)) +                        // softwareEnforced
        tlv(0x30, ByteArray(0))                          // teeEnforced
    return tlv(0x30, body)
}

class AttestationParserTest {

    @Test fun extractsChallengeAndSecurityLevel() {
        val challenge = ByteArray(32) { (it + 1).toByte() }
        val info = AttestationParser.parseKeyDescription(keyDescription(challenge, securityLevel = 1))!!
        assertArrayEquals(challenge, info.challenge)
        assertEquals(1, info.securityLevel)
    }

    @Test fun strongBoxLevelParses() {
        val info = AttestationParser.parseKeyDescription(keyDescription(ByteArray(4), securityLevel = 2))!!
        assertEquals(2, info.securityLevel)
    }

    @Test fun malformedReturnsNull() {
        assertNull(AttestationParser.parseKeyDescription(byteArrayOf(0x30, 0x01, 0x02)))
        assertNull(AttestationParser.parseKeyDescription(tlv(0x30, tlv(0x02, byteArrayOf(1))))) // too few fields
    }
}
