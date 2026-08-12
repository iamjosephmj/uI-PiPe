package tech.ssemaj.pipe.channel

import java.util.concurrent.atomic.AtomicLong
import tech.ssemaj.pipe.core.PipeMessage

/** Stamps outgoing messages with a monotonically increasing seq. */
class OutboundSequencer {
    private val next = AtomicLong(0)
    fun stamp(message: PipeMessage): PipeMessage = message.copy(seq = next.getAndIncrement())
}

/**
 * Drops duplicate/regressed messages. Oneway binder calls to one node arrive in order,
 * so this is a safety net; gaps are accepted (sender may have skipped seqs).
 */
class InboundSequencer {
    private var expected = 0L

    @Synchronized
    fun accept(message: PipeMessage): PipeMessage? {
        if (message.seq == PipeMessage.UNSET_SEQ) return message
        if (message.seq < expected) return null
        expected = message.seq + 1
        return message
    }
}
