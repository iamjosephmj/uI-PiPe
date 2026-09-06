package tech.ssemaj.pipe.provider

import tech.ssemaj.pipe.auth.PeerIdentity
import tech.ssemaj.pipe.core.PipeMessage

/**
 * The provider app's handle to the verified host (and to its own pane's lifecycle). Handed to
 * [onOpenPane][PipeProviderService.onOpenPane]; valid for the life of that open.
 */
interface HostHandle {
    /** The host's verified identity, captured from the binder before the pane was allowed to open. */
    val peer: PeerIdentity

    /**
     * Sends [message] to the host. Returns `true` if the binder call was dispatched, `false`
     * if the host is gone — a `false` is terminal for this handle, not a retryable send.
     */
    suspend fun send(message: PipeMessage): Boolean

    /** Closes this pane from the provider side (removes the window, notifies the host). */
    fun close()
}
