package tech.ssemaj.pipe.core

import tech.ssemaj.pipe.auth.PeerIdentity

/** Live connection state of a [tech.ssemaj.pipe.host.PipeSession]. Denial is NOT a
 *  state — a denied open() throws instead of returning a session. */
sealed interface PipeState {
    data object Connecting : PipeState
    data class Open(val peer: PeerIdentity) : PipeState
    /** [cause] null ⇒ clean close; non-null ⇒ closed by failure. */
    data class Closed(val cause: PipeException?) : PipeState
}
