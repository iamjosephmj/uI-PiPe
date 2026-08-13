package tech.ssemaj.pipe.host

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import tech.ssemaj.pipe.auth.PeerIdentity
import tech.ssemaj.pipe.channel.InboundSequencer
import tech.ssemaj.pipe.channel.OutboundSequencer
import tech.ssemaj.pipe.core.CloseReason
import tech.ssemaj.pipe.core.DenialSource
import tech.ssemaj.pipe.core.PipeDeniedException
import tech.ssemaj.pipe.core.PipeException
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeState
import tech.ssemaj.pipe.core.PipeTransportException
import tech.ssemaj.pipe.internal.ignoringRemote
import tech.ssemaj.pipe.transport.IEmbedProvider
import tech.ssemaj.pipe.transport.IEmbedSession
import tech.ssemaj.pipe.transport.IGuestChannel
import tech.ssemaj.pipe.transport.IHostChannel
import tech.ssemaj.pipe.transport.IOpenResultCallback
import tech.ssemaj.pipe.transport.OpenSpec
import tech.ssemaj.pipe.transport.Protocol

/**
 * A single open of one provider pane, from the moment [PipeConnection] admits it to the moment it
 * closes. It binds the provider service, sends the host window token, wires the two channels, and
 * exposes the live [session].
 *
 * The state machine has one terminal transition, reached by exactly one of:
 *  - [terminate] — the open never produced a live pane (denied, errored, bind failed, timed out);
 *    the [deferred] completes exceptionally and callers of `open()` see the [PipeException].
 *  - [close] — a pane that *was* live is torn down (host close, provider close, peer death); the
 *    [session] moves to [PipeState.Closed].
 *
 * On that terminal transition [onTerminal] fires so [PipeConnection] can forget this attempt.
 * Everything except binder callbacks runs on the main thread (posted through [mainHandler]).
 */
internal class OpenAttempt(
    private val context: Context,
    private val provider: ProviderComponent,
    private val request: tech.ssemaj.pipe.core.PipeRequest,
    private val hostToken: () -> IBinder?,
    private val mainHandler: Handler,
    private val deferred: CompletableDeferred<PipeSession>,
    private val onTerminal: (OpenAttempt) -> Unit,
) {
    var verifiedPeer: PeerIdentity? = null
    private var remoteSession: IEmbedSession? = null
    private var guestChannel: IGuestChannel? = null
    private var bound = false
    private var closed = false
    private val outbound = OutboundSequencer()
    private val inbound = InboundSequencer()
    private val stateFlow = MutableStateFlow<PipeState>(PipeState.Connecting)
    private val messageListeners = CopyOnWriteArrayList<SendChannel<PipeMessage>>()

    /** The live session handed to the caller once [openCallbackStub] reports the pane is open. */
    val session: PipeSession = object : PipeSession {
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
            ignoringRemote {
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
        mainHandler.postDelayed({ whenTokenReady(block) }, TOKEN_POLL_MS)
    }

    private fun sendOpen(remote: IEmbedProvider) {
        if (closed) return
        try {
            val token = hostToken() ?: error("host window token unavailable")
            remote.open(OpenSpec(token, request, Protocol.VERSION), hostChannelStub, openCallbackStub)
        } catch (t: Throwable) {
            terminate(PipeTransportException("open() failed: ${t.message}", t))
        }
    }

    /** Uid captured at gate time; -1 means unresolvable, so the check is skipped. */
    private fun callerUidMismatch(): Boolean {
        val expected = verifiedPeer?.uid ?: -1
        return expected != -1 && Binder.getCallingUid() != expected
    }

    private val hostChannelStub = object : IHostChannel.Stub() {
        override fun send(message: PipeMessage) {
            if (callerUidMismatch()) return
            val accepted = inbound.accept(message) ?: return
            mainHandler.post { if (!closed) messageListeners.forEach { it.trySend(accepted) } }
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
                if (closed) { ignoringRemote { session.close() }; return@post }
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
            mainHandler.post { terminate(message.toPipeException()) }
        }
    }

    /**
     * The provider process/connection is gone (binder death, service disconnect). Pre-open this is a
     * failed attempt ([terminate]); post-open it's an unexpected close of a live session, reported
     * with [CloseReason.PEER_DIED].
     */
    fun providerGone(ex: PipeTransportException) {
        if (remoteSession == null) terminate(ex) else close(CloseReason.PEER_DIED, notifyProvider = false, cause = ex)
    }

    /** Terminal transition for an attempt that never opened a live pane. */
    fun terminate(ex: PipeException) {
        if (closed) return
        // A protocol-violating provider can call onOpened then fail: route that through close so the
        // live session/window is released and its Closed cause reflects [ex].
        if (remoteSession != null) {
            close(CloseReason.PROVIDER_CLOSED, notifyProvider = false, cause = ex)
            return
        }
        closed = true
        unbind()
        onTerminal(this)
        deferred.completeExceptionally(ex)
    }

    /** Terminal transition for a pane that was live. */
    fun close(reason: CloseReason, notifyProvider: Boolean, cause: PipeException? = null) {
        if (closed) return
        closed = true
        if (notifyProvider) ignoringRemote { remoteSession?.close() }
        unbind()
        remoteSession = null
        guestChannel = null
        onTerminal(this)
        stateFlow.value = PipeState.Closed(cause)
        messageListeners.forEach { it.close() }
        messageListeners.clear()
    }

    private fun unbind() {
        if (bound) runCatching { context.unbindService(connection) }
        bound = false
    }

    private companion object { const val TOKEN_POLL_MS = 16L }
}
