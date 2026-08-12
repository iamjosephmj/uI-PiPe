package tech.ssemaj.pipe.provider

import android.app.Service
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.SurfaceControlViewHost
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tech.ssemaj.pipe.auth.AndroidSigningSource
import tech.ssemaj.pipe.auth.IdentityResolver
import tech.ssemaj.pipe.auth.PeerIdentity
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.auth.PipeAuthorizers
import tech.ssemaj.pipe.channel.InboundSequencer
import tech.ssemaj.pipe.channel.OutboundSequencer
import tech.ssemaj.pipe.core.CloseReason
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.core.PipeSize
import tech.ssemaj.pipe.transport.IEmbedProvider
import tech.ssemaj.pipe.transport.IEmbedSession
import tech.ssemaj.pipe.transport.IGuestChannel
import tech.ssemaj.pipe.transport.IHostChannel
import tech.ssemaj.pipe.transport.IOpenResultCallback
import tech.ssemaj.pipe.transport.OpenSpec
import tech.ssemaj.pipe.transport.Protocol

/**
 * Base class for pane provider services. Subclass, implement [onOpenPane],
 * export with action tech.ssemaj.pipe.action.OPEN_PANE.
 *
 * Multi-pane: each distinct host that opens a pane gets its own independent
 * [ActivePane], keyed by that host's callback binder. Closing/dying of one
 * host's pane never affects any other host's pane.
 */
abstract class PipeProviderService : Service() {

    /** Policy for who may embed this pane. Default: same signing key. */
    open fun authorizer(): PipeAuthorizer = PipeAuthorizers.sameSigningKey(this)

    /** Build the pane (or reject the request). Runs in [paneScope] (main thread). */
    abstract suspend fun onOpenPane(request: PipeRequest, host: HostHandle): PaneResult

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Service-owned scope for gating + pane construction + provider-initiated sends.
     * Exposed as protected so provider apps can launch sends against [HostHandle.send].
     */
    protected val paneScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val panes = ConcurrentHashMap<IBinder, ActivePane>()

    final override fun onBind(intent: Intent?): IBinder = object : IEmbedProvider.Stub() {
        override fun protocolVersion(): Int = Protocol.VERSION

        override fun open(spec: OpenSpec, hostChannel: IHostChannel, callback: IOpenResultCallback) {
            // Must be read on the binder thread, before any dispatch/coroutine hop —
            // Binder.getCallingUid() only reflects the caller inside the transaction.
            val callingUid = Binder.getCallingUid()
            val gate = ProviderGate(IdentityResolver(AndroidSigningSource(this@PipeProviderService)), authorizer())
            paneScope.launch {
                val result = gate.admit(callingUid, spec.request, spec.protocolVersion)
                when (result) {
                    is GateResult.Refused -> callback.onDenied(result.reason)
                    is GateResult.Failed -> callback.onError(result.message)
                    is GateResult.Admitted -> {
                        try {
                            openOnMain(spec, result.peer, hostChannel, callback)
                        } catch (t: Throwable) {
                            runCatching { callback.onError("provider failed to open pane: ${t.message}") }
                        }
                    }
                }
            }
        }
    }

    private suspend fun openOnMain(
        spec: OpenSpec,
        peer: PeerIdentity,
        hostChannel: IHostChannel,
        callback: IOpenResultCallback,
    ) {
        val outbound = OutboundSequencer()
        val hostHandle = object : HostHandle {
            override val peer: PeerIdentity = peer
            override suspend fun send(message: PipeMessage): Boolean =
                runCatching { hostChannel.send(outbound.stamp(message)) }.isSuccess
        }
        when (val result = onOpenPane(spec.request, hostHandle)) {
            is PaneResult.Reject -> {
                callback.onDenied(result.reason)
                return
            }
            is PaneResult.Content -> {
                val content = result.content
                val display = getSystemService(DisplayManager::class.java).getDisplay(spec.displayId)
                val scvh = SurfaceControlViewHost(this, display, spec.inputTransferToken)
                scvh.setView(content.view, spec.widthPx, spec.heightPx)

                val hostBinder = hostChannel.asBinder()
                val pane = ActivePane(scvh, content, hostChannel, hostBinder, mainHandler, peer.uid)
                try {
                    // Host death → tear down only this host's pane.
                    hostChannel.asBinder()
                        .linkToDeath({ mainHandler.post { pane.close(CloseReason.PEER_DIED) } }, 0)
                    panes[hostBinder] = pane
                    callback.onOpened(scvh.surfacePackage, pane.session, pane.guestChannel)
                } catch (t: Throwable) {
                    pane.close(CloseReason.PROVIDER_CLOSED)
                    throw t
                }
            }
        }
    }

    override fun onDestroy() {
        panes.values.toList().forEach { it.close(CloseReason.PROVIDER_CLOSED) }
        panes.clear()
        paneScope.cancel()
        super.onDestroy()
    }

    private inner class ActivePane(
        private val scvh: SurfaceControlViewHost,
        private val content: PipeContent,
        private val hostChannel: IHostChannel,
        private val hostBinder: IBinder,
        private val handler: Handler,
        /** Kernel-derived uid of the admitted host; -1 (unresolvable) skips the check. */
        private val hostUid: Int,
    ) {
        private val inbound = InboundSequencer()
        private var closed = false

        private fun callerUidMismatch(): Boolean =
            hostUid != -1 && Binder.getCallingUid() != hostUid

        val session = object : IEmbedSession.Stub() {
            override fun resize(widthPx: Int, heightPx: Int) {
                if (callerUidMismatch()) return
                handler.post {
                    if (closed) return@post
                    scvh.relayout(widthPx, heightPx)
                    content.onResized(PipeSize(widthPx, heightPx))
                }
            }
            override fun close() {
                if (callerUidMismatch()) return
                handler.post { close(CloseReason.HOST_CLOSED) }
            }
        }

        val guestChannel = object : IGuestChannel.Stub() {
            override fun send(message: PipeMessage) {
                if (callerUidMismatch()) return
                val accepted = inbound.accept(message) ?: return
                handler.post { if (!closed) content.onMessage(accepted) }
            }
        }

        fun close(reason: CloseReason) {
            if (closed) return
            closed = true
            runCatching { scvh.release() }
            content.onClosed(reason)
            if (reason != CloseReason.HOST_CLOSED) {
                runCatching { hostChannel.onClosed(reason.toWire()) }
            }
            panes.remove(hostBinder, this)
        }
    }
}
