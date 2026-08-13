package tech.ssemaj.pipe.host

import org.junit.Assert.assertTrue
import org.junit.Test
import tech.ssemaj.pipe.core.PipeProviderUnavailableException
import tech.ssemaj.pipe.core.PipeVersionMismatchException

class GateFailureMappingTest {
    @Test fun mapsCertUnreadable() {
        val e = "unavailable:CERT_UNREADABLE".toPipeException()
        assertTrue(e is PipeProviderUnavailableException &&
            e.kind == PipeProviderUnavailableException.Unavailable.CERT_UNREADABLE)
    }
    @Test fun mapsProtocol() {
        assertTrue("protocol version mismatch: host=2 provider=1".toPipeException() is PipeVersionMismatchException)
    }
}
