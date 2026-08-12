package tech.ssemaj.pipe.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CoreTypesTest {
    @Test fun messageDefaultsToUnsetSeqAndSchemaV1() {
        // Bundle is an Android type; on JVM unit tests it is a stub, so only touch defaults here.
        assertEquals(-1L, PipeMessage.UNSET_SEQ)
    }

    @Test fun closeReasonRoundTripsThroughWire() {
        for (reason in CloseReason.entries) {
            assertEquals(reason, CloseReason.fromWire(reason.toWire()))
        }
    }

    @Test fun unknownCloseReasonWireValueMapsToPeerDied() {
        assertEquals(CloseReason.PEER_DIED, CloseReason.fromWire(999))
    }
}
