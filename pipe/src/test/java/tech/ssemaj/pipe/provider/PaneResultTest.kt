package tech.ssemaj.pipe.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class PaneResultTest {
    @Test fun rejectCarriesReason() {
        val r: PaneResult = PaneResult.Reject("unknown action")
        assertEquals("unknown action", (r as PaneResult.Reject).reason)
    }
}
