package tech.ssemaj.pipe.host

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.auth.PipeAuthorizers
import tech.ssemaj.pipe.core.PipeException
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.core.PipeState

/**
 * Opens a verified provider's full-screen pane over [activity] — the host-side entry point.
 *
 * The pane is a window the provider adds on top of the host, parented to the host's window token —
 * there is nothing to place in the host's own layout. This call binds the provider, verifies it
 * against [authorizer], and delivers the live [PipeSession] to [onSession] (or a [PipeException]
 * to [onError]). The pane is torn down when the session closes from any side, on `ON_DESTROY`, or
 * on back-press — whichever comes first. Back inside the pane is handled by the provider's own
 * window; the host back-press callback here is the fallback for when focus is still on the host.
 *
 * [bindImportance] controls how much of the host's process priority the provider's process inherits
 * while the pane is open; it defaults to [PipeBindImportance.NORMAL] (the pane ranks below the host).
 *
 * [timeout] bounds the whole handshake — bind, verification, and the provider's `onOpenPane` —
 * after which the open fails with a [PipeTimeoutException][tech.ssemaj.pipe.core.PipeTimeoutException].
 * The 10 s default is generous for a cold provider process; shorten it for time-critical UX, or
 * length it if your provider's `onOpenPane` does slow work (key generation, network) before
 * returning content.
 *
 * [dimSystemBars] dims the host's status/navigation-bar strips while the pane is open — a pane is a
 * sub-window of the host and cannot itself draw over those strips, so a provider's full-bleed scrim
 * (e.g. a dialog pane with `PaneSpec(edgeToEdge = true)`) leaves the bars undimmed unless the host
 * dims them. Use it for scrim/dialog-style panes; leave it off for region/tiling panes.
 *
 * The returned [Job] is the open itself; it completes once the session is delivered or the open has
 * failed. Remember: the provider you bind must be declared in your manifest's `<queries>` element
 * (package visibility, API 30+) or the open fails with
 * [PipeProviderUnavailableException][tech.ssemaj.pipe.core.PipeProviderUnavailableException].
 */
object PipeFullScreen {
    /** Default bound on bind + verification + `onOpenPane`. */
    val DEFAULT_OPEN_TIMEOUT: Duration = 10.seconds

    fun open(
        activity: ComponentActivity,
        provider: ProviderComponent,
        request: PipeRequest,
        authorizer: PipeAuthorizer = PipeAuthorizers.sameSigningKey(activity),
        bindImportance: PipeBindImportance = PipeBindImportance.NORMAL,
        timeout: Duration = DEFAULT_OPEN_TIMEOUT,
        dimSystemBars: Boolean = false,
        onSession: (PipeSession) -> Unit = {},
        onError: (PipeException) -> Unit = {},
    ): Job {
        val connection = PipeConnection(activity, hostToken = { activity.window.decorView.windowToken })
        val barDim = if (dimSystemBars) SystemBarDim(activity) else null

        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = connection.close()
        }
        activity.lifecycle.addObserver(observer)

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = connection.close()
        }
        activity.onBackPressedDispatcher.addCallback(activity, backCallback)

        fun detach() {
            backCallback.isEnabled = false
            backCallback.remove()
            activity.lifecycle.removeObserver(observer)
            barDim?.detach()
        }

        return activity.lifecycleScope.launch {
            try {
                val session = connection.open(provider, request, authorizer, timeout, bindImportance)
                barDim?.attach()
                // The session can end other ways than back-press (host close, provider close, peer
                // death) — drop the observer/back callback then too, so they never leak.
                activity.lifecycleScope.launch {
                    session.state.first { it is PipeState.Closed }
                    detach()
                }
                onSession(session)
            } catch (e: PipeException) {
                detach()
                onError(e)
            }
        }
    }
}
