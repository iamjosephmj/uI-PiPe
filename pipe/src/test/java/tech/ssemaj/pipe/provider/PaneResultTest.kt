package tech.ssemaj.pipe.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PaneResultTest {
    @Test fun rejectCarriesReason() {
        val r: PaneResult = PaneResult.Reject("unknown action")
        assertEquals("unknown action", (r as PaneResult.Reject).reason)
    }

    @Test fun defaultSpecIsInsetSafeNotEdgeToEdge() {
        // Default panes are inset to the system bars (content never underlaps them);
        // edge-to-edge is an explicit opt-in for full-bleed scrims.
        assertFalse(PaneSpec().edgeToEdge)
    }
}
