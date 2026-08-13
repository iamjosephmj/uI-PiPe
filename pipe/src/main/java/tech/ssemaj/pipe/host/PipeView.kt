package tech.ssemaj.pipe.host

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceControlViewHost.SurfacePackage
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import tech.ssemaj.pipe.auth.AndroidSigningSource
import tech.ssemaj.pipe.auth.IdentityResolver
import tech.ssemaj.pipe.auth.PeerIdentity
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.auth.PipeAuthorizers
import tech.ssemaj.pipe.channel.InboundSequencer
import tech.ssemaj.pipe.channel.OutboundSequencer
import tech.ssemaj.pipe.core.CloseReason
import tech.ssemaj.pipe.core.DenialSource
import tech.ssemaj.pipe.core.PipeDeniedException
import tech.ssemaj.pipe.core.PipeException
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipePresentation
import tech.ssemaj.pipe.core.PipeProviderUnavailableException
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.core.PipeSize
import tech.ssemaj.pipe.core.PipeState
import tech.ssemaj.pipe.core.PipeTimeoutException
import tech.ssemaj.pipe.core.PipeTransportException
import tech.ssemaj.pipe.core.PipeVersionMismatchException
import tech.ssemaj.pipe.provider.GateResult
import tech.ssemaj.pipe.transport.IEmbedProvider
import tech.ssemaj.pipe.transport.IEmbedSession
import tech.ssemaj.pipe.transport.IGuestChannel
import tech.ssemaj.pipe.transport.IHostChannel
import tech.ssemaj.pipe.transport.IOpenResultCallback
import tech.ssemaj.pipe.transport.OpenSpec
import tech.ssemaj.pipe.transport.Protocol

private const val TAG = "PipeView"

/** API 35 gates the public InputTransferToken/transferTouchGesture path; below it uses host tokens. */
private val API35 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

/**
 * Maps a [GateResult.Failed] message (host-side gate, or a provider-side onError string —
 * both funnel through here) to the typed [PipeException] callers see.
 */
internal fun gateFailureToException(message: String): PipeException = when {
    message == "unavailable:CERT_UNREADABLE" ->
        PipeProviderUnavailableException(PipeProviderUnavailableException.Unavailable.CERT_UNREADABLE)
    message == "unavailable:NOT_VISIBLE" ->
        PipeProviderUnavailableException(PipeProviderUnavailableException.Unavailable.NOT_VISIBLE)
    message.startsWith("protocol") -> {
        val hostVersion = Regex("host=(\\d+)").find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val providerVersion = Regex("provider=(\\d+)").find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        PipeVersionMismatchException(hostVersion, providerVersion)
    }
    else -> PipeTransportException(message)
}

/** Embeds a verified provider's pane. Add to a layout, call [open]. */
class PipeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : FrameLayout(context, attrs) {

    private val surfaceView = SurfaceView(context).also {
        // Host content stays visually on top; the embedded pane is composited beneath it and
        // touch is handed over explicitly (see [embeddedInputToken]) rather than via Z order.
        it.setZOrderOnTop(false)
        addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var embeddedInputToken: android.window.InputTransferToken? = null
    private var current: OpenAttempt? = null
    @Volatile private var directInput = false

    init {
        // A regular SurfaceView does not forward touches into an embedded
        // SurfaceControlViewHost automatically; the host must hand each gesture off, on every
        // ACTION_DOWN, by transferring from its own input token to the embedded pane's token.
        // Non-embedded (full-screen/dialog) panes skip this: the embedded surface is rendered on
        // top via setZOrderOnTop and receives input directly, so it accepts repeated gestures
        // without depending on per-gesture transfer.
        surfaceView.setOnTouchListener { _, event ->
            // Only API 35+ needs (and has) transferTouchGesture. On API 30–34 the embedded pane is
            // linked via the host input token at construction and receives touch directly.
            if (API35 && !directInput && event.actionMasked == MotionEvent.ACTION_DOWN) {
                val embedded = embeddedInputToken
                val hostToken = surfaceView.rootSurfaceControl?.inputTransferToken
                if (embedded != null && hostToken != null) {
                    runCatching { windowManager?.transferTouchGesture(hostToken, embedded) }
                        .onSuccess { transferred ->
                            if (transferred == false) {
                                Log.w(TAG, "transferTouchGesture returned false; pane may not receive touch")
                            }
                        }
                        .onFailure { t -> Log.w(TAG, "transferTouchGesture threw; pane may not receive touch", t) }
                }
            }
            false
        }
    }

    /**
     * Opens [provider] and suspends until the pane is live, throwing a [PipeException] on any
     * failure (denial, timeout, transport error, protocol mismatch). One pane per view: calling
     * this while a session from a prior [open] is still live throws [IllegalStateException] —
     * close it first.
     */
    suspend fun open(
        provider: ProviderComponent,
        request: PipeRequest,
        authorizer: PipeAuthorizer = PipeAuthorizers.sameSigningKey(context),
        timeout: Duration = 10.seconds,
    ): PipeSession = withContext(dispatcher) {
        check(current == null) { "PipeView already has a live session; call close() first" }
        val deferred = CompletableDeferred<PipeSession>()
        val attempt = OpenAttempt(provider, request, deferred)
        current = attempt
        // Non-embedded panes always take input directly (surface on top). On API < 35 there is no
        // transferTouchGesture, so embedded panes must do the same to receive touch at all — the
        // host-token link plus a top-ordered surface routes input straight to the embedded window.
        if (request.presentation != PipePresentation.EMBEDDED || !API35) {
            directInput = true
            surfaceView.setZOrderOnTop(true)
        }
        try {
            withTimeout(timeout) {
                val gate = HostGate(IdentityResolver(AndroidSigningSource(context)), authorizer)
                // Cert lookups touch PackageManager; keep them off the main thread. The
                // authorizer itself may suspend arbitrarily — that's fine, it's not blocking.
                when (val result = withContext(Dispatchers.IO) { gate.admit(provider, request) }) {
                    is GateResult.Refused ->
                        attempt.terminate(PipeDeniedException(result.reason, DenialSource.HOST_POLICY))
                    is GateResult.Failed ->
                        attempt.terminate(gateFailureToException(result.message))
                    is GateResult.Admitted -> {
                        attempt.verifiedPeer = result.peer
                        attempt.bind()
                    }
                }
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            attempt.terminate(PipeTimeoutException())
            throw PipeTimeoutException()
        }
    }

    /**
     * Convenience wrapper: launches [open] on [owner]'s lifecycle scope, delivers the result to
     * [onSession]/[onError], and closes the session automatically at `ON_DESTROY`.
     */
    fun openIn(
        owner: LifecycleOwner,
        provider: ProviderComponent,
        request: PipeRequest,
        authorizer: PipeAuthorizer = PipeAuthorizers.sameSigningKey(context),
        onError: (PipeException) -> Unit = {},
        onSession: (PipeSession) -> Unit = {},
    ): Job {
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                close()
            }
        }
        owner.lifecycle.addObserver(observer)
        return owner.lifecycleScope.launch {
            try {
                val session = open(provider, request, authorizer)
                // Drop the ON_DESTROY observer as soon as the session closes on its own, so it
                // doesn't sit on owner.lifecycle (retaining this PipeView) until activity destroy.
                // A separate lifecycle-scoped coroutine so it doesn't delay this Job's completion
                // (which callers observe as "open() finished, onSession/onError delivered").
                owner.lifecycleScope.launch {
                    session.state.first { it is PipeState.Closed }
                    owner.lifecycle.removeObserver(observer)
                }
                onSession(session)
            } catch (e: PipeException) {
                owner.lifecycle.removeObserver(observer)
                onError(e)
            }
        }
    }

    /** Closes the current session (if any). Safe to call with no session open. */
    fun close() {
        current?.close(CloseReason.HOST_CLOSED, notifyProvider = true)
        current = null
    }

    override fun onDetachedFromWindow() {
        close()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val attempt = current
        if (attempt != null) {
            runCatching { attempt.remoteSession?.resize(w, h) }
        }
    }

    private inner class OpenAttempt(
        val provider: ProviderComponent,
        val request: PipeRequest,
        val deferred: CompletableDeferred<PipeSession>,
    ) {
        var verifiedPeer: PeerIdentity? = null
        var remoteSession: IEmbedSession? = null
        private var guestChannel: IGuestChannel? = null
        private var bound = false
        internal var closed = false
        private val outbound = OutboundSequencer()
        private val inbound = InboundSequencer()
        private val stateFlow = MutableStateFlow<PipeState>(PipeState.Connecting)
        private val messageListeners = CopyOnWriteArrayList<SendChannel<PipeMessage>>()

        val session = object : PipeSession {
            override val peer: PeerIdentity get() = checkNotNull(verifiedPeer) { "session not open" }
            override val state = stateFlow.asStateFlow()
            override val messages: Flow<PipeMessage> = callbackFlow {
                if (closed) { close(); return@callbackFlow }
                messageListeners += channel
                awaitClose { messageListeners -= channel }
            }
            override suspend fun send(message: PipeMessage): Boolean =
                guestChannel?.let { runCatching { it.send(outbound.stamp(message)) }.isSuccess } ?: false
            override suspend fun resize(size: PipeSize) {
                runCatching { remoteSession?.resize(size.widthPx, size.heightPx) }
            }
            override fun close() { mainHandler.post { close(CloseReason.HOST_CLOSED, notifyProvider = true) } }
        }

        private val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val remote = IEmbedProvider.Stub.asInterface(binder)
                runCatching {
                    binder.linkToDeath(
                        { mainHandler.post { providerGone(PipeTransportException("provider process died")) } },
                        0,
                    )
                }
                whenAttached { sendOpen(remote) }
            }
            override fun onServiceDisconnected(name: ComponentName) {
                mainHandler.post { providerGone(PipeTransportException("provider service disconnected")) }
            }
        }

        fun bind() {
            val intent = Intent("tech.ssemaj.pipe.action.OPEN_PANE").setComponent(provider.toComponentName())
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                runCatching { context.unbindService(connection) }
                terminate(PipeTransportException("bindService returned false"))
            }
        }

        /** Wait until the SurfaceView is attached so the host input token can be obtained. */
        private fun whenAttached(block: () -> Unit) {
            val ready = if (API35) surfaceView.rootSurfaceControl != null else windowToken != null
            if (isAttachedToWindow && ready) { mainHandler.post { block() }; return }
            surfaceView.post { whenAttached(block) }
        }

        private fun sendOpen(remote: IEmbedProvider) {
            if (closed) return
            try {
                val spec = if (API35) {
                    val token = surfaceView.rootSurfaceControl?.inputTransferToken
                        ?: throw IllegalStateException("no inputTransferToken; view not attached")
                    newSpec(hostToken = null, inputToken = token)
                } else {
                    val host = hostInputTokenPre35()
                        ?: throw IllegalStateException("no pre-35 host input token (getHostToken unavailable)")
                    newSpec(hostToken = host, inputToken = null)
                }
                remote.open(spec, hostChannelStub, openCallbackStub)
            } catch (t: Throwable) {
                terminate(PipeTransportException("open() failed: ${t.message}", t))
            }
        }

        private fun newSpec(hostToken: IBinder?, inputToken: android.os.Parcelable?) = OpenSpec(
            hostToken = hostToken,
            inputToken = inputToken,
            displayId = display.displayId,
            widthPx = width.coerceAtLeast(1),
            heightPx = height.coerceAtLeast(1),
            request = request,
            protocolVersion = Protocol.VERSION,
        )

        /**
         * The host input token on API 30–34. `SurfaceView.getHostToken()` is `@hide` before API 35,
         * so it is reached by reflection; if non-SDK restrictions block it, falls back to the
         * window token. Returns null only if neither is available.
         */
        private fun hostInputTokenPre35(): IBinder? {
            runCatching {
                val m = SurfaceView::class.java.getDeclaredMethod("getHostToken").apply { isAccessible = true }
                (m.invoke(surfaceView) as? IBinder)?.let { return it }
            }.onFailure { Log.w(TAG, "getHostToken() unavailable; falling back to windowToken", it) }
            return windowToken
        }

        /** Uid captured at gate time; -1 means unresolvable, so the check is skipped. */
        private val expectedUid: Int get() = verifiedPeer?.uid ?: -1

        private fun callerUidMismatch(): Boolean {
            val expected = expectedUid
            return expected != -1 && Binder.getCallingUid() != expected
        }

        private val hostChannelStub = object : IHostChannel.Stub() {
            override fun send(message: PipeMessage) {
                if (callerUidMismatch()) return
                val accepted = inbound.accept(message) ?: return
                mainHandler.post {
                    if (!closed) messageListeners.forEach { it.trySend(accepted) }
                }
            }
            override fun onClosed(closeReasonWire: Int) {
                if (callerUidMismatch()) return
                mainHandler.post {
                    val reason = CloseReason.fromWire(closeReasonWire)
                    if (remoteSession == null) {
                        terminate(PipeTransportException("provider closed before open (reason=$reason)"))
                    } else {
                        close(reason, notifyProvider = false)
                    }
                }
            }
        }

        private val openCallbackStub = object : IOpenResultCallback.Stub() {
            override fun onOpened(surfacePackage: SurfacePackage, session: IEmbedSession, guest: IGuestChannel) {
                mainHandler.post {
                    if (closed) { runCatching { session.close() }; return@post }
                    remoteSession = session
                    guestChannel = guest
                    surfaceView.setChildSurfacePackage(surfacePackage)
                    embeddedInputToken = if (API35) runCatching { surfacePackage.inputTransferToken }.getOrNull() else null
                    stateFlow.value = PipeState.Open(checkNotNull(verifiedPeer) { "onOpened without a verified peer" })
                    deferred.complete(this@OpenAttempt.session)
                }
            }
            override fun onDenied(reason: String) {
                mainHandler.post { terminate(PipeDeniedException(reason, DenialSource.PROVIDER_POLICY)) }
            }
            override fun onError(message: String) {
                mainHandler.post { terminate(gateFailureToException(message)) }
            }
        }

        /**
         * Tear down a never-opened attempt (denied or errored before onOpened, bindService()
         * returning false, a local failure while sending open(), or a timeout) by failing the
         * [deferred] with [ex] — nothing was ever opened, so there is nothing to "close".
         *
         * If the provider actually opened a live surface before failing (called onOpened, then
         * onDenied/onError — a protocol violation, or a race with a timeout), there IS something
         * to close: route through the real close path instead so the surface/session/channel are
         * released and the session's [PipeState] reflects [ex] as the closing cause. In that case
         * [deferred] is already completed (successfully) and this is a no-op for it.
         */
        /**
         * The provider process/connection is gone (binder death, service disconnect). Pre-open
         * this is a failed attempt ([terminate]); post-open it's an unexpected close of a live
         * session, reported with the accurate [CloseReason.PEER_DIED] rather than the generic
         * reason [terminate] would apply.
         */
        fun providerGone(ex: PipeTransportException) {
            if (remoteSession == null) {
                terminate(ex)
            } else {
                close(CloseReason.PEER_DIED, notifyProvider = false, cause = ex)
            }
        }

        fun terminate(ex: PipeException) {
            if (closed) return
            if (remoteSession != null) {
                close(CloseReason.PROVIDER_CLOSED, notifyProvider = false, cause = ex)
                return
            }
            closed = true
            if (bound) runCatching { context.unbindService(connection) }
            bound = false
            if (current === this@OpenAttempt) {
                current = null; embeddedInputToken = null
                directInput = false; surfaceView.setZOrderOnTop(false)
            }
            deferred.completeExceptionally(ex)
        }

        fun close(reason: CloseReason, notifyProvider: Boolean, cause: PipeException? = null) {
            if (closed) return
            closed = true
            if (notifyProvider) runCatching { remoteSession?.close() }
            if (bound) runCatching { context.unbindService(connection) }
            bound = false
            remoteSession = null
            guestChannel = null
            if (current === this@OpenAttempt) {
                current = null; embeddedInputToken = null
                directInput = false; surfaceView.setZOrderOnTop(false)
            }
            stateFlow.value = PipeState.Closed(cause)
            messageListeners.forEach { it.close() }
            messageListeners.clear()
        }
    }
}
