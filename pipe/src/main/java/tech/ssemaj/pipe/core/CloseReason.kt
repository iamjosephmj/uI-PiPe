package tech.ssemaj.pipe.core

/**
 * Why a pane came down. Delivered to [PipeContent.onClosed]
 * [provider-side][tech.ssemaj.pipe.provider.PipeContent.onClosed] and folded into
 * [PipeState.Closed] host-side.
 */
enum class CloseReason(private val wire: Int) {
    /** The host closed the session (`session.close()`, back-press, or activity destroy). */
    HOST_CLOSED(0),

    /** The provider closed its own pane (`host.close()`, or BACK inside the pane's window). */
    PROVIDER_CLOSED(1),

    /** The peer process died; the library tore the pane down. */
    PEER_DIED(2),

    ;

    /** Stable wire representation (do not change — it is part of the protocol). */
    fun toWire(): Int = wire

    companion object {
        /** Inverse of [toWire]; an unknown wire value maps to [PEER_DIED] (fail-safe teardown). */
        fun fromWire(v: Int): CloseReason = entries.firstOrNull { it.wire == v } ?: PEER_DIED
    }
}
