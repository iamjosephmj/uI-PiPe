package tech.ssemaj.pipe.provider

import tech.ssemaj.pipe.auth.PeerIdentity
import tech.ssemaj.pipe.core.PipeMessage

/** Provider app's handle to the (verified) host and its own pane. */
interface HostHandle {
    val peer: PeerIdentity
    suspend fun send(message: PipeMessage): Boolean
    /** Closes this pane from the provider side (removes the window, notifies the host). */
    fun close()
}
