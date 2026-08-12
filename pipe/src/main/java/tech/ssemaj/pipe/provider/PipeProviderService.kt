package tech.ssemaj.pipe.provider

import android.app.Service
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.SurfaceControlViewHost
import kotlinx.coroutines.runBlocking
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
 */
abstract class PipeProviderService : Service() {

    /** Policy for who may embed this pane. Default: same signing key. */
    open fun authorizer(): PipeAuthorizer = PipeAuthorizers.sameSigningKey(this)

    /** Build the pane. Called on the main thread after the caller passed the gate. */
    abstract fun onOpenPane(request: PipeRequest, host: HostHandle): PipeContent

    private val mainHandler = Handler(Looper.getMainLooper())
    private var active: ActivePane? = null

    final override fun onBind(intent: Intent?): IBinder = object : IEmbedProvider.Stub() {
        override fun protocolVersion(): Int = Protocol.VERSION

        override fun open(spec: OpenSpec, hostChannel: IHostChannel, callback: IOpenResultCallback) {
            val gate = ProviderGate(IdentityResolver(AndroidSigningSource(this@PipeProviderService)), authorizer())
            // TEMPORARY (removed in Task 8 when this service gets a coroutine scope):
            // bridges the now-suspend ProviderGate.admit to this non-suspend AIDL binder
            // method (IEmbedProvider.Stub.open runs on a binder thread). WARNING:
            // runBlocking here runs on the calling binder thread. An authorizer that
            // hops to Dispatchers.Main / posts to a Handler and awaits it will DEADLOCK
            // until this shim is removed in Task 8. Until then, only non-dispatching
            // authorizers (cert checks, allowlist) are safe.
            val result = runBlocking { gate.admit(Binder.getCallingUid(), spec.request, spec.protocolVersion) }
            when (result) {
                is GateResult.Refused -> { callback.onDenied(result.reason); return }
                is GateResult.Failed -> { callback.onError(result.message); return }
                is GateResult.Admitted -> mainHandler.post {
                    try {
                        openOnMain(spec, result.peer, hostChannel, callback)
                    } catch (t: Throwable) {
                        runCatching { callback.onError("provider failed to open pane: ${t.message}") }
                    }
                }
            }
        }
    }

    private fun openOnMain(
        spec: OpenSpec,
        peer: PeerIdentity,
        hostChannel: IHostChannel,
        callback: IOpenResultCallback,
    ) {
        active?.close(CloseReason.PROVIDER_CLOSED) // one live pane per service in v1
        val outbound = OutboundSequencer()
        val hostHandle = object : HostHandle {
            override val peer: PeerIdentity = peer
            override fun send(message: PipeMessage) {
                runCatching { hostChannel.send(outbound.stamp(message)) }
            }
        }
        val content = onOpenPane(spec.request, hostHandle)
        val display = getSystemService(DisplayManager::class.java).getDisplay(spec.displayId)
        val scvh = SurfaceControlViewHost(this, display, spec.inputTransferToken)
        scvh.setView(content.view, spec.widthPx, spec.heightPx)

        val pane = ActivePane(scvh, content, hostChannel, mainHandler, peer.uid)
        try {
            // Host death → tear down our side.
            hostChannel.asBinder().linkToDeath({ mainHandler.post { pane.close(CloseReason.PEER_DIED) } }, 0)
            active = pane
            callback.onOpened(scvh.surfacePackage, pane.session, pane.guestChannel)
        } catch (t: Throwable) {
            pane.close(CloseReason.PROVIDER_CLOSED)
            throw t
        }
    }

    override fun onDestroy() {
        active?.close(CloseReason.PROVIDER_CLOSED)
        active = null
        super.onDestroy()
    }

    private inner class ActivePane(
        private val scvh: SurfaceControlViewHost,
        private val content: PipeContent,
        private val hostChannel: IHostChannel,
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
                    content.onResized(widthPx, heightPx)
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
            if (active === this) active = null
        }
    }
}
