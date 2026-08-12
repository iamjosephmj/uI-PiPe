package tech.ssemaj.pipe.host

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.AttributeSet
import android.view.SurfaceControlViewHost.SurfacePackage
import android.view.SurfaceView
import android.widget.FrameLayout
import tech.ssemaj.pipe.auth.AndroidSigningSource
import tech.ssemaj.pipe.auth.EmbedAuthorizer
import tech.ssemaj.pipe.auth.EmbedAuthorizers
import tech.ssemaj.pipe.auth.IdentityResolver
import tech.ssemaj.pipe.auth.PeerIdentity
import tech.ssemaj.pipe.channel.InboundSequencer
import tech.ssemaj.pipe.channel.OutboundSequencer
import tech.ssemaj.pipe.core.CloseReason
import tech.ssemaj.pipe.core.PipeError
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.provider.GateResult
import tech.ssemaj.pipe.transport.IEmbedProvider
import tech.ssemaj.pipe.transport.IEmbedSession
import tech.ssemaj.pipe.transport.IGuestChannel
import tech.ssemaj.pipe.transport.IHostChannel
import tech.ssemaj.pipe.transport.IOpenResultCallback
import tech.ssemaj.pipe.transport.OpenSpec
import tech.ssemaj.pipe.transport.Protocol

/** Embeds a verified provider's pane. Add to a layout, call [open]. */
class PipeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val surfaceView = SurfaceView(context).also {
        addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var current: OpenAttempt? = null

    fun open(
        provider: ProviderComponent,
        request: PipeRequest,
        authorizer: EmbedAuthorizer = EmbedAuthorizers.sameSigningKey(context),
        callbacks: PipeHostCallbacks,
    ): PipeSession {
        closePane() // one pane per view
        val attempt = OpenAttempt(provider, request, callbacks)
        current = attempt

        val gate = HostGate(IdentityResolver(AndroidSigningSource(context)), authorizer)
        when (val result = gate.admit(provider, request)) {
            is GateResult.Refused -> {
                current = null
                attempt.closed = true
                mainHandler.post { callbacks.onDenied(result.reason) }
                return attempt.session
            }
            is GateResult.Failed -> {
                current = null
                attempt.closed = true
                mainHandler.post { callbacks.onError(PipeError(PipeError.Code.PROVIDER_NOT_FOUND, result.message)) }
                return attempt.session
            }
            is GateResult.Admitted -> attempt.verifiedPeer = result.peer
        }
        // Verified == bound: bind the exact ComponentName we just verified.
        attempt.bind()
        return attempt.session
    }

    fun closePane() {
        current?.close(CloseReason.HOST_CLOSED, notifyProvider = true)
        current = null
    }

    override fun onDetachedFromWindow() {
        closePane()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val attempt = current
        if (attempt != null) {
            runCatching {
                attempt.remoteSession?.resize(w, h)
                attempt.callbacks.onResized(w, h)
            }
        }
    }

    private inner class OpenAttempt(
        val provider: ProviderComponent,
        val request: PipeRequest,
        val callbacks: PipeHostCallbacks,
    ) {
        var verifiedPeer: PeerIdentity? = null
        var remoteSession: IEmbedSession? = null
        private var guestChannel: IGuestChannel? = null
        private var bound = false
        internal var closed = false
        private val outbound = OutboundSequencer()
        private val inbound = InboundSequencer()

        val session = object : PipeSession {
            override val peer: PeerIdentity get() = checkNotNull(verifiedPeer) { "session not open" }
            override fun send(message: PipeMessage) {
                runCatching { guestChannel?.send(outbound.stamp(message)) }
            }
            override fun resize(widthPx: Int, heightPx: Int) {
                runCatching { remoteSession?.resize(widthPx, heightPx) }
            }
            override fun close() { mainHandler.post { close(CloseReason.HOST_CLOSED, notifyProvider = true) } }
        }

        private val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val remote = IEmbedProvider.Stub.asInterface(binder)
                runCatching { binder.linkToDeath({ mainHandler.post { close(CloseReason.PEER_DIED, notifyProvider = false) } }, 0) }
                whenAttached { sendOpen(remote) }
            }
            override fun onServiceDisconnected(name: ComponentName) {
                mainHandler.post { close(CloseReason.PEER_DIED, notifyProvider = false) }
            }
        }

        fun bind() {
            val intent = Intent("tech.ssemaj.pipe.action.OPEN_PANE").setComponent(provider.toComponentName())
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                runCatching { context.unbindService(connection) }
                mainHandler.post {
                    callbacks.onError(PipeError(PipeError.Code.PROVIDER_NOT_FOUND, "bindService returned false"))
                }
            }
        }

        /** Wait until the SurfaceView is attached so rootSurfaceControl/token exist. */
        private fun whenAttached(block: () -> Unit) {
            if (isAttachedToWindow && surfaceView.rootSurfaceControl != null) { mainHandler.post { block() }; return }
            surfaceView.post { whenAttached(block) }
        }

        private fun sendOpen(remote: IEmbedProvider) {
            if (closed) return
            try {
                val token = surfaceView.rootSurfaceControl?.inputTransferToken
                    ?: throw IllegalStateException("no inputTransferToken; view not attached")
                val spec = OpenSpec(
                    inputTransferToken = token,
                    displayId = display.displayId,
                    widthPx = width.coerceAtLeast(1),
                    heightPx = height.coerceAtLeast(1),
                    request = request,
                    protocolVersion = Protocol.VERSION,
                )
                remote.open(spec, hostChannelStub, openCallbackStub)
            } catch (t: Throwable) {
                callbacks.onError(PipeError(PipeError.Code.TRANSPORT_FAILURE, "open() failed: ${t.message}"))
                close(CloseReason.PEER_DIED, notifyProvider = false)
            }
        }

        private val hostChannelStub = object : IHostChannel.Stub() {
            override fun send(message: PipeMessage) {
                val accepted = inbound.accept(message) ?: return
                mainHandler.post { if (!closed) callbacks.onMessage(accepted) }
            }
            override fun onClosed(closeReasonWire: Int) {
                mainHandler.post { close(CloseReason.fromWire(closeReasonWire), notifyProvider = false) }
            }
        }

        private val openCallbackStub = object : IOpenResultCallback.Stub() {
            override fun onOpened(surfacePackage: SurfacePackage, session: IEmbedSession, guest: IGuestChannel) {
                mainHandler.post {
                    if (closed) { runCatching { session.close() }; return@post }
                    remoteSession = session
                    guestChannel = guest
                    surfaceView.setChildSurfacePackage(surfacePackage)
                    callbacks.onOpened(this@OpenAttempt.session)
                }
            }
            override fun onDenied(reason: String) {
                mainHandler.post { callbacks.onDenied(reason); close(CloseReason.PROVIDER_CLOSED, notifyProvider = false) }
            }
            override fun onError(message: String) {
                mainHandler.post {
                    callbacks.onError(PipeError(PipeError.Code.TRANSPORT_FAILURE, message))
                    close(CloseReason.PROVIDER_CLOSED, notifyProvider = false)
                }
            }
        }

        fun close(reason: CloseReason, notifyProvider: Boolean) {
            if (closed) return
            closed = true
            if (notifyProvider) runCatching { remoteSession?.close() }
            if (bound) runCatching { context.unbindService(connection) }
            bound = false
            remoteSession = null
            guestChannel = null
            callbacks.onClosed(reason)
            if (current === this) current = null
        }
    }
}
