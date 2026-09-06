package tech.ssemaj.pipe.provider

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.FrameLayout
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tech.ssemaj.pipe.auth.AndroidPackageManagerSource
import tech.ssemaj.pipe.auth.IdentityResolver
import tech.ssemaj.pipe.auth.PeerIdentity
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.auth.PipeAuthorizers
import tech.ssemaj.pipe.channel.OutboundSequencer
import tech.ssemaj.pipe.core.CloseReason
import tech.ssemaj.pipe.core.GateResult
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.internal.ignoringRemote
import tech.ssemaj.pipe.transport.IEmbedProvider
import tech.ssemaj.pipe.transport.IHostChannel
import tech.ssemaj.pipe.transport.IOpenResultCallback
import tech.ssemaj.pipe.transport.OpenSpec
import tech.ssemaj.pipe.transport.Protocol

private const val TAG = "PipeProviderService"

/**
 * Base class for pane provider services. Subclass, implement [onOpenPane], and export the service
 * with the `tech.ssemaj.pipe.action.OPEN_PANE` action.
 *
 * A pane is a single full-screen window the provider adds over the host (see [addPane]); the pane's
 * lifetime and input plumbing are handled by [PaneRoot] and [ActivePane]. This class is only the
 * coordinator: it gates the caller, invokes [onOpenPane], adds the window, and keeps one
 * [ActivePane] per host binder so that hosts stay independent.
 */
abstract class PipeProviderService : Service() {

    /** Policy for who may open this pane. Default: same signing key. */
    open fun authorizer(): PipeAuthorizer = PipeAuthorizers.sameSigningKey(this)

    /** Build the pane (or reject the request). Runs in [paneScope] (main thread). */
    abstract suspend fun onOpenPane(request: PipeRequest, host: HostHandle): PaneResult

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Service-owned scope for gating + pane construction + provider-initiated sends.
     * Exposed as protected so provider apps can launch sends against [HostHandle.send].
     */
    protected val paneScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, t -> Log.w(TAG, "pane coroutine failed", t) },
    )

    /** One live pane per host, keyed by that host's callback binder. */
    private val panes = ConcurrentHashMap<IBinder, ActivePane>()

    final override fun onBind(intent: Intent?): IBinder = object : IEmbedProvider.Stub() {
        override fun protocolVersion(): Int = Protocol.VERSION

        override fun open(spec: OpenSpec, hostChannel: IHostChannel, callback: IOpenResultCallback) {
            // Must be read on the binder thread, before any dispatch/coroutine hop —
            // Binder.getCallingUid() only reflects the caller inside the transaction.
            val callingUid = Binder.getCallingUid()
            val gate = ProviderGate(IdentityResolver(AndroidPackageManagerSource(this@PipeProviderService)), authorizer())
            paneScope.launch {
                when (val result = gate.admit(callingUid, spec.request, spec.protocolVersion)) {
                    is GateResult.Refused -> ignoringRemote { callback.onDenied(result.reason) }
                    is GateResult.Failed -> ignoringRemote { callback.onError(result.message) }
                    is GateResult.Admitted -> openAdmitted(spec, result.peer, hostChannel, callback)
                }
            }
        }
    }

    /** Runs on [paneScope] (main thread) once the caller is admitted. */
    private suspend fun openAdmitted(
        spec: OpenSpec,
        peer: PeerIdentity,
        hostChannel: IHostChannel,
        callback: IOpenResultCallback,
    ) {
        try {
            val host = PaneHostHandle(peer, hostChannel)
            when (val result = onOpenPane(spec.request, host)) {
                is PaneResult.Reject -> ignoringRemote { callback.onDenied(result.reason) }
                is PaneResult.Content -> mountPane(spec, result, hostChannel, host, callback)
            }
        } catch (t: Throwable) {
            ignoringRemote { callback.onError("provider failed to open pane: ${t.message ?: t.javaClass.simpleName}") }
        }
    }

    /** Builds the window per [result]'s spec, registers the pane, and hands the host its binders. */
    private fun mountPane(
        spec: OpenSpec,
        result: PaneResult.Content,
        hostChannel: IHostChannel,
        host: PaneHostHandle,
        callback: IOpenResultCallback,
    ) {
        val content = result.content
        val hostBinder = hostChannel.asBinder()
        // Fill a full-screen (MATCH) window; wrap a sized (region) window to its content.
        fun fit(v: Int) = if (v == WindowManager.LayoutParams.MATCH_PARENT) v else FrameLayout.LayoutParams.WRAP_CONTENT
        val root = PaneRoot(this, fitSystemBars = !result.spec.edgeToEdge).apply {
            addView(content.view, FrameLayout.LayoutParams(fit(result.spec.widthPx), fit(result.spec.heightPx)))
        }
        val windowManager = getSystemService(WindowManager::class.java)
        windowManager.addPane(root, spec.hostToken, result.spec)

        val pane = ActivePane(
            windowManager, root, content, hostChannel, mainHandler, host.peer.uid,
            onClosed = { panes.remove(hostBinder, it) },
        )
        // BACK in the pane's own focusable window dismisses it; so does HostHandle.close().
        root.onBack = { pane.close(CloseReason.PROVIDER_CLOSED) }
        host.onClose = { mainHandler.post { pane.close(CloseReason.PROVIDER_CLOSED) } }
        try {
            // Host death → tear down only this host's pane.
            hostBinder.linkToDeath({ mainHandler.post { pane.close(CloseReason.PEER_DIED) } }, 0)
            // Same-host re-open: release the previous pane for this binder so its window isn't
            // leaked. A different host's binder is a different key, so it is unaffected.
            panes.put(hostBinder, pane)?.close(CloseReason.PROVIDER_CLOSED)
            callback.onOpened(pane.session, pane.guestChannel)
        } catch (t: Throwable) {
            pane.close(CloseReason.PROVIDER_CLOSED)
            throw t
        }
    }

    override fun onDestroy() {
        panes.values.toList().forEach { it.close(CloseReason.PROVIDER_CLOSED) }
        panes.clear()
        paneScope.cancel()
        super.onDestroy()
    }

    /**
     * The provider app's handle back to the verified host. [onClose] is wired once the pane is live
     * (see [mountPane]); until then [close] is a no-op, which is safe because the provider only
     * receives this handle from [onOpenPane], by which point the pane is about to be mounted.
     */
    private inner class PaneHostHandle(
        override val peer: PeerIdentity,
        private val hostChannel: IHostChannel,
    ) : HostHandle {
        private val outbound = OutboundSequencer()
        var onClose: (() -> Unit)? = null

        override suspend fun send(message: PipeMessage): Boolean =
            runCatching { hostChannel.send(outbound.stamp(message)) }.isSuccess

        override fun close() { onClose?.invoke() }
    }
}
