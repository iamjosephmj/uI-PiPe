package tech.ssemaj.pipe.provider

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineExceptionHandler
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
 * A pane is a single full-screen `TYPE_APPLICATION_PANEL` window the provider adds over the host,
 * parented to the host activity's window token (carried in [OpenSpec.hostToken]). Because it is a
 * real window in the host's hierarchy, it is a first-class focus/IME/input target on every API
 * from 30 up — no `SurfaceControlViewHost`, no `@hide` APIs, no touch forwarding.
 *
 * Multi-host: each distinct host that opens a pane gets its own independent [ActivePane], keyed by
 * that host's callback binder. Closing/dying of one host's pane never affects another's.
 */
abstract class PipeProviderService : Service() {

    /** Policy for who may embed this pane. Default: same signing key. */
    open fun authorizer(): PipeAuthorizer = PipeAuthorizers.sameSigningKey(this)

    /** Build the pane (or reject the request). Runs in [paneScope] (main thread). */
    abstract suspend fun onOpenPane(request: tech.ssemaj.pipe.core.PipeRequest, host: HostHandle): PaneResult

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Service-owned scope for gating + pane construction + provider-initiated sends.
     * Exposed as protected so provider apps can launch sends against [HostHandle.send].
     */
    protected val paneScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, t -> Log.w("PipeProviderService", "pane coroutine failed", t) },
    )

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
                    is GateResult.Refused -> runCatching { callback.onDenied(result.reason) }
                    is GateResult.Failed -> runCatching { callback.onError(result.message) }
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
        // `closer` is wired once the pane exists (below); until then close() is a no-op — the
        // provider only holds the handle after onOpenPane returns, by which point it is set.
        var closer: (() -> Unit)? = null
        val hostHandle = object : HostHandle {
            override val peer: PeerIdentity = peer
            override suspend fun send(message: PipeMessage): Boolean =
                runCatching { hostChannel.send(outbound.stamp(message)) }.isSuccess
            override fun close() { closer?.invoke() }
        }
        when (val result = onOpenPane(spec.request, hostHandle)) {
            is PaneResult.Reject -> {
                callback.onDenied(result.reason)
                return
            }
            is PaneResult.Content -> {
                val content = result.content
                val hostBinder = hostChannel.asBinder()
                val match = FrameLayout.LayoutParams.MATCH_PARENT
                val root = PaneRoot(this).apply {
                    addView(content.view, FrameLayout.LayoutParams(match, match))
                }
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                    0,
                    PixelFormat.OPAQUE,
                ).apply {
                    token = spec.hostToken
                    gravity = Gravity.TOP or Gravity.START
                    softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                }
                val windowManager = getSystemService(WindowManager::class.java)
                windowManager.addView(root, lp)

                val pane = ActivePane(windowManager, root, content, hostChannel, hostBinder, mainHandler, peer.uid)
                // BACK inside the pane's own (focusable) window dismisses it, provider-side; so does
                // an explicit HostHandle.close() from provider content.
                root.onBack = { pane.close(CloseReason.PROVIDER_CLOSED) }
                closer = { mainHandler.post { pane.close(CloseReason.PROVIDER_CLOSED) } }
                try {
                    // Host death → tear down only this host's pane.
                    hostBinder.linkToDeath({ mainHandler.post { pane.close(CloseReason.PEER_DIED) } }, 0)
                    // Same-host re-open: close and release the previously registered pane for this
                    // host binder so its window isn't leaked. A different host's binder is a
                    // different map key, so it is unaffected.
                    panes.put(hostBinder, pane)?.close(CloseReason.PROVIDER_CLOSED)
                    callback.onOpened(pane.session, pane.guestChannel)
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

    /**
     * Root of the pane's window. Pads itself by the system-bar insets so provider content is never
     * drawn under the status/navigation bars, and turns a BACK key press (this window is focusable,
     * so it receives keys) into a provider-side dismissal via [onBack].
     */
    private class PaneRoot(context: Context) : FrameLayout(context) {
        var onBack: (() -> Unit)? = null

        init {
            isFocusableInTouchMode = true
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                onBack?.invoke()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    private inner class ActivePane(
        private val windowManager: WindowManager,
        private val root: FrameLayout,
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
            runCatching { windowManager.removeViewImmediate(root) }
            content.onClosed(reason)
            if (reason != CloseReason.HOST_CLOSED) {
                runCatching { hostChannel.onClosed(reason.toWire()) }
            }
            panes.remove(hostBinder, this)
        }
    }
}
