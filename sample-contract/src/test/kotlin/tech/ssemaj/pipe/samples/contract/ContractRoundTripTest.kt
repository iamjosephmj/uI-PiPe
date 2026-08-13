package tech.ssemaj.pipe.samples.contract

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class ContractRoundTripTest {

    @Test fun requestRoundTrips() {
        val req = CertificationRequest(nonce = ByteArray(32) { it.toByte() }, hostDisplayName = "Pipe Sample Host")
        val back = Cbor.decodeFromByteArray<CertificationRequest>(Cbor.encodeToByteArray(req))
        assertArrayEquals(req.nonce, back.nonce)
        assertEquals("Pipe Sample Host", back.hostDisplayName)
    }

    @Test fun grantedRoundTripsThroughSealedSupertype() {
        val granted: CertificationResponse = CertificationResponse.Granted(
            signature = byteArrayOf(1, 2, 3),
            certChainDer = listOf(byteArrayOf(4, 5), byteArrayOf(6)),
            securityLevel = SecurityLevel.HARDWARE,
        )
        val back = Cbor.decodeFromByteArray<CertificationResponse>(Cbor.encodeToByteArray(granted))
        assertTrue(back is CertificationResponse.Granted)
        back as CertificationResponse.Granted
        assertArrayEquals(byteArrayOf(1, 2, 3), back.signature)
        assertEquals(2, back.certChainDer.size)
        assertEquals(SecurityLevel.HARDWARE, back.securityLevel)
    }

    @Test fun declinedRoundTripsThroughSealedSupertype() {
        val declined: CertificationResponse = CertificationResponse.Declined("user said no")
        val back = Cbor.decodeFromByteArray<CertificationResponse>(Cbor.encodeToByteArray(declined))
        assertTrue(back is CertificationResponse.Declined)
        assertEquals("user said no", (back as CertificationResponse.Declined).reason)
    }
}
