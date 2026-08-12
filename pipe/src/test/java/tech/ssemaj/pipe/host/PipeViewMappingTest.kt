package tech.ssemaj.pipe.host

import org.junit.Assert.assertTrue
import org.junit.Test
import tech.ssemaj.pipe.core.PipeProviderUnavailableException
import tech.ssemaj.pipe.core.PipeVersionMismatchException

class PipeViewMappingTest {
    @Test fun mapsCertUnreadable() {
        val e = gateFailureToException("unavailable:CERT_UNREADABLE")
        assertTrue(e is PipeProviderUnavailableException &&
            e.kind == PipeProviderUnavailableException.Unavailable.CERT_UNREADABLE)
    }
    @Test fun mapsProtocol() {
        assertTrue(gateFailureToException("protocol version mismatch: host=2 provider=1") is PipeVersionMismatchException)
    }
}
