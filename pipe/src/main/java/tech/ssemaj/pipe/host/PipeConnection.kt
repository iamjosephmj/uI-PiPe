package tech.ssemaj.pipe.host

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import tech.ssemaj.pipe.auth.AndroidSigningSource
import tech.ssemaj.pipe.auth.IdentityResolver
import tech.ssemaj.pipe.auth.PeerIdentity
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.channel.InboundSequencer
import tech.ssemaj.pipe.channel.OutboundSequencer
import tech.ssemaj.pipe.core.CloseReason
import tech.ssemaj.pipe.core.DenialSource
import tech.ssemaj.pipe.core.PipeDeniedException
import tech.ssemaj.pipe.core.PipeException
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeProviderUnavailableException
import tech.ssemaj.pipe.core.PipeRequest
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

/**
 * Host-side connection to a provider pane. Binds the provider service, runs the host gate, sends
 * the host window token so the provider can add its full-screen pane, and exposes the live
 * [PipeSession]. Holds no UI — the pane is a window the provider owns; the host only controls the
 * session (messages, close). One connection drives one pane at a time.
 *
 * [hostToken] supplies the host activity's window token (`decorView.windowToken`); [open] waits
 * for it to become non-null (the decor view must be attached) before sending the open request.
 */
internal class PipeConnection(
    private val context: Context,
    private val hostToken: () -> IBinder?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var current: OpenAttempt? = null

    /**
     * Binds [provider] and suspends until the pane is live, throwing a [PipeException] on any
     * failure (denial, timeout, transport error, protocol mismatch). One pane per connection:
     * calling this while a prior session is still live throws [IllegalStateException].
     */
    suspend fun open(
        provider: ProviderComponent,
        request: PipeRequest,
        authorizer: PipeAuthorizer,
        timeout: Duration,
    ): PipeSession = withContext(dispatcher) {
        check(current == null) { "PipeConnection already has a live session; call close() first" }
        val deferred = CompletableDeferred<PipeSession>()
        val attempt = OpenAttempt(provider, request, deferred)
        current = attempt
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

    /** Closes the current session (if any). Safe to call with no session open. */
    fun close() {
        current?.close(CloseReason.HOST_CLOSED, notifyProvider = true)
        current = null
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
                whenTokenReady { sendOpen(remote) }
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

        /** Wait until the host activity's window token is available (decor view attached). */
        private fun whenTokenReady(block: () -> Unit) {
            if (closed) return
            if (hostToken() != null) { mainHandler.post { block() }; return }
            mainHandler.postDelayed({ whenTokenReady(block) }, 16)
        }

        private fun sendOpen(remote: IEmbedProvider) {
            if (closed) return
            try {
                val token = hostToken() ?: throw IllegalStateException("host window token unavailable")
                val spec = OpenSpec(
                    hostToken = token,
                    request = request,
                    protocolVersion = Protocol.VERSION,
                )
                remote.open(spec, hostChannelStub, openCallbackStub)
            } catch (t: Throwable) {
                terminate(PipeTransportException("open() failed: ${t.message}", t))
            }
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
            override fun onOpened(session: IEmbedSession, guest: IGuestChannel) {
                mainHandler.post {
                    if (closed) { runCatching { session.close() }; return@post }
                    remoteSession = session
                    guestChannel = guest
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
         * The provider process/connection is gone (binder death, service disconnect). Pre-open
         * this is a failed attempt ([terminate]); post-open it's an unexpected close of a live
         * session, reported with [CloseReason.PEER_DIED].
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
            if (current === this@OpenAttempt) current = null
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
            if (current === this@OpenAttempt) current = null
            stateFlow.value = PipeState.Closed(cause)
            messageListeners.forEach { it.close() }
            messageListeners.clear()
        }
    }
}
