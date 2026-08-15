package tech.ssemaj.pipe.samples.kyc

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class KycContractRoundTripTest {
    @Test fun request_roundtrips() {
        val req = KycRequest(reference = "ref-123", level = KycLevel.ENHANCED)
        val back = Cbor.decodeFromByteArray<KycRequest>(Cbor.encodeToByteArray(req))
        assertEquals(req, back)
    }

    @Test fun result_roundtrips() {
        val res = KycResult(reference = "ref-123", status = KycStatus.APPROVED, issuedAtEpochMs = 1_723_680_000_000L)
        val back = Cbor.decodeFromByteArray<KycResult>(Cbor.encodeToByteArray(res))
        assertEquals(res, back)
    }
}
