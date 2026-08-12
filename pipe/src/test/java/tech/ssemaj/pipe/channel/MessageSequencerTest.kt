package tech.ssemaj.pipe.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import tech.ssemaj.pipe.core.PipeMessage

// PipeMessage's Bundle field is never touched here, so the JVM Bundle stub is safe to construct lazily:
private fun msg(seq: Long = PipeMessage.UNSET_SEQ) = PipeMessage(payload = android.os.Bundle(), seq = seq)

class MessageSequencerTest {
    @Test fun outboundStampsMonotonicallyFromZero() {
        val out = OutboundSequencer()
        assertEquals(0L, out.stamp(msg()).seq)
        assertEquals(1L, out.stamp(msg()).seq)
        assertEquals(2L, out.stamp(msg()).seq)
    }

    @Test fun inboundAcceptsInOrder() {
        val inbound = InboundSequencer()
        assertNotNull(inbound.accept(msg(seq = 0)))
        assertNotNull(inbound.accept(msg(seq = 1)))
    }

    @Test fun inboundDropsDuplicates() {
        val inbound = InboundSequencer()
        assertNotNull(inbound.accept(msg(seq = 0)))
        assertNull(inbound.accept(msg(seq = 0)))
    }

    @Test fun inboundDropsRegressions() {
        val inbound = InboundSequencer()
        assertNotNull(inbound.accept(msg(seq = 5)))
        assertNull(inbound.accept(msg(seq = 3)))
    }

    @Test fun inboundAcceptsGapsAndAdvances() {
        val inbound = InboundSequencer()
        assertNotNull(inbound.accept(msg(seq = 0)))
        assertNotNull(inbound.accept(msg(seq = 7)))
        assertNull(inbound.accept(msg(seq = 7)))
    }

    @Test fun inboundPassesThroughUnsetSeq() {
        val inbound = InboundSequencer()
        assertNotNull(inbound.accept(msg()))
        assertNotNull(inbound.accept(msg()))
    }
}
