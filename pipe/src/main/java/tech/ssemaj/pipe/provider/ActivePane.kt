package tech.ssemaj.pipe.provider

import android.os.Binder
import android.os.Handler
import android.view.View
import android.view.WindowManager
import tech.ssemaj.pipe.channel.InboundSequencer
import tech.ssemaj.pipe.core.CloseReason
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.internal.ignoringRemote
import tech.ssemaj.pipe.transport.IEmbedSession
import tech.ssemaj.pipe.transport.IGuestChannel

/**
 * One host's live pane: the window it owns plus the two binder stubs the host drives it through.
 *
 * Each host that opens a pane gets its own [ActivePane]; they are independent, so one closing or
 * dying never touches another. Every inbound binder call is UID-gated against the host admitted at
 * open time ([hostUid]) — even a leaked binder handle cannot drive this pane from another UID.
 *
 * Teardown is idempotent and converges from every trigger (host close, provider close via
 * [CloseReason.PROVIDER_CLOSED], peer death): the window is removed, the content is notified, the
 * host is told (unless it initiated the close), and [onClosed] unregisters this pane.
 */
internal class ActivePane(
    private val windowManager: WindowManager,
    private val root: View,
    private val content: PipeContent,
    private val hostChannel: tech.ssemaj.pipe.transport.IHostChannel,
    private val handler: Handler,
    /** Kernel-derived uid of the admitted host; -1 (unresolvable) skips the check. */
    private val hostUid: Int,
    /** Called once, on close, so the service can drop this pane from its registry. */
    private val onClosed: (ActivePane) -> Unit,
) {
    private val inbound = InboundSequencer()
    private var closed = false

    /** Drop any inbound call whose caller isn't the host we admitted (unless uid was unresolvable). */
    private fun callerUidMismatch(): Boolean =
        hostUid != -1 && Binder.getCallingUid() != hostUid

    /** Host's control handle: the host can close the pane. */
    val session: IEmbedSession = object : IEmbedSession.Stub() {
        override fun close() {
            if (callerUidMismatch()) return
            handler.post { close(CloseReason.HOST_CLOSED) }
        }
    }

    /** Host → provider messages, delivered in order to [PipeContent.onMessage] on the main thread. */
    val guestChannel: IGuestChannel = object : IGuestChannel.Stub() {
        override fun send(message: PipeMessage) {
            if (callerUidMismatch()) return
            val accepted = inbound.accept(message) ?: return
            handler.post { if (!closed) content.onMessage(accepted) }
        }
    }

    fun close(reason: CloseReason) {
        if (closed) return
        closed = true
        content.onClosed(reason)
        // The host already knows when it asked to close; only tell it about closes it didn't start.
        if (reason != CloseReason.HOST_CLOSED) {
            ignoringRemote { hostChannel.onClosed(reason.toWire()) }
        }
        onClosed(this)
        ignoringRemote { windowManager.removeViewImmediate(root) }
    }
}
