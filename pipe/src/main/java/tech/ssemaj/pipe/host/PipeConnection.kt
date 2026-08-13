package tech.ssemaj.pipe.host

import android.content.Context
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import tech.ssemaj.pipe.auth.AndroidSigningSource
import tech.ssemaj.pipe.auth.IdentityResolver
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.core.CloseReason
import tech.ssemaj.pipe.core.DenialSource
import tech.ssemaj.pipe.core.PipeDeniedException
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.core.PipeTimeoutException
import tech.ssemaj.pipe.provider.GateResult

/**
 * Host-side coordinator for a provider pane. Runs the host gate, then drives one [OpenAttempt] at a
 * time to bind the provider and hand it the host window token so it can add its full-screen pane.
 *
 * Holds no UI — the pane is a window the provider owns; the host only controls the session (messages,
 * close). [hostToken] supplies the host activity's window token (`decorView.windowToken`); the
 * attempt waits for it to become non-null before sending the open request.
 */
internal class PipeConnection(
    private val context: Context,
    private val hostToken: () -> IBinder?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var current: OpenAttempt? = null

    /**
     * Binds [provider] and suspends until the pane is live, throwing a [tech.ssemaj.pipe.core.PipeException]
     * on any failure (denial, timeout, transport error, protocol mismatch). One pane per connection:
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
        val attempt = OpenAttempt(context, provider, request, hostToken, mainHandler, deferred, ::clearCurrent)
        current = attempt
        try {
            withTimeout(timeout) {
                admit(provider, request, authorizer, attempt)
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            attempt.terminate(PipeTimeoutException())
            throw PipeTimeoutException()
        }
    }

    /** Runs the host gate off the main thread (cert lookups touch PackageManager), then binds. */
    private suspend fun admit(
        provider: ProviderComponent,
        request: PipeRequest,
        authorizer: PipeAuthorizer,
        attempt: OpenAttempt,
    ) {
        val gate = HostGate(IdentityResolver(AndroidSigningSource(context)), authorizer)
        when (val result = withContext(Dispatchers.IO) { gate.admit(provider, request) }) {
            is GateResult.Refused -> attempt.terminate(PipeDeniedException(result.reason, DenialSource.HOST_POLICY))
            is GateResult.Failed -> attempt.terminate(result.message.toPipeException())
            is GateResult.Admitted -> {
                attempt.verifiedPeer = result.peer
                attempt.bind()
            }
        }
    }

    /** Closes the current session (if any). Safe to call with no session open. */
    fun close() {
        current?.close(CloseReason.HOST_CLOSED, notifyProvider = true)
        current = null
    }

    private fun clearCurrent(attempt: OpenAttempt) {
        if (current === attempt) current = null
    }
}
