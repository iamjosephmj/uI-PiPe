package tech.ssemaj.pipe.provider

import android.app.Service
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.SurfaceControlViewHost
import tech.ssemaj.pipe.auth.AndroidSigningSource
import tech.ssemaj.pipe.auth.EmbedAuthorizer
import tech.ssemaj.pipe.auth.EmbedAuthorizers
import tech.ssemaj.pipe.auth.IdentityResolver
import tech.ssemaj.pipe.auth.PeerIdentity
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
    open fun authorizer(): EmbedAuthorizer = EmbedAuthorizers.sameSigningKey(this)

    /** Build the pane. Called on the main thread after the caller passed the gate. */
    abstract fun onOpenPane(request: PipeRequest, host: HostHandle): PipeContent

    private val mainHandler = Handler(Looper.getMainLooper())
    private var active: ActivePane? = null

    final override fun onBind(intent: Intent?): IBinder = object : IEmbedProvider.Stub() {
        override fun protocolVersion(): Int = Protocol.VERSION

        override fun open(spec: OpenSpec, hostChannel: IHostChannel, callback: IOpenResultCallback) {
            val gate = ProviderGate(IdentityResolver(AndroidSigningSource(this@PipeProviderService)), authorizer())
            val result = gate.admit(Binder.getCallingUid(), spec.request, spec.protocolVersion)
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

        val pane = ActivePane(scvh, content, hostChannel, mainHandler)
        active = pane
        // Host death → tear down our side.
        hostChannel.asBinder().linkToDeath({ mainHandler.post { pane.close(CloseReason.PEER_DIED) } }, 0)
        callback.onOpened(scvh.surfacePackage, pane.session, pane.guestChannel)
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
    ) {
        private val inbound = InboundSequencer()
        private var closed = false

        val session = object : IEmbedSession.Stub() {
            override fun resize(widthPx: Int, heightPx: Int) {
                handler.post {
                    if (closed) return@post
                    scvh.relayout(widthPx, heightPx)
                    content.onResized(widthPx, heightPx)
                }
            }
            override fun close() { handler.post { close(CloseReason.HOST_CLOSED) } }
        }

        val guestChannel = object : IGuestChannel.Stub() {
            override fun send(message: PipeMessage) {
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
