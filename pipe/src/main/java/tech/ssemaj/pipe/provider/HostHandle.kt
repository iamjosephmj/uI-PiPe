package tech.ssemaj.pipe.provider

import tech.ssemaj.pipe.auth.PeerIdentity
import tech.ssemaj.pipe.core.PipeMessage

/** Provider app's handle back to the (verified) host. */
interface HostHandle {
    val peer: PeerIdentity
    suspend fun send(message: PipeMessage): Boolean
}
