package tech.ssemaj.pipe.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreV2TypesTest {
    @Test fun actionConstantIsStable() {
        assertEquals("tech.ssemaj.pipe.action.OPEN_PANE", Pipe.ACTION_OPEN_PANE)
    }

    @Test fun deniedExceptionCarriesReasonAndSource() {
        val e = PipeDeniedException("nope", DenialSource.PROVIDER_POLICY)
        assertEquals("nope", e.reason)
        assertEquals(DenialSource.PROVIDER_POLICY, e.source)
        assertTrue(e is PipeException)
    }

    @Test fun unavailableExceptionExposesKind() {
        val e = PipeProviderUnavailableException(PipeProviderUnavailableException.Unavailable.NOT_VISIBLE)
        assertEquals(PipeProviderUnavailableException.Unavailable.NOT_VISIBLE, e.kind)
    }

    @Test fun closedStateWithoutCauseIsClean() {
        val s = PipeState.Closed(cause = null)
        assertNull(s.cause)
    }

    @Test fun pipeSizeIsValueType() {
        assertEquals(PipeSize(4, 2), PipeSize(4, 2))
    }
}
