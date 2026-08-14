package tech.ssemaj.pipe.host

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.auth.PipeAuthorizers
import tech.ssemaj.pipe.core.PipeException
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.core.PipeState

/**
 * Opens a verified provider's full-screen pane over [activity].
 *
 * The pane is a window the provider adds on top of the host, parented to the host's window token —
 * there is nothing to place in the host's own layout. This call binds the provider, verifies it
 * against [authorizer], and delivers the live [PipeSession] to [onSession] (or a [PipeException] to
 * [onError]). The pane is torn down when the session closes from any side, on `ON_DESTROY`, or on
 * back-press — whichever comes first. Back inside the pane is handled by the provider's own window;
 * the host back-press callback here is the fallback for when focus is still on the host.
 *
 * [bindImportance] controls how much of the host's process priority the provider's process inherits
 * while the pane is open; it defaults to [PipeBindImportance.NORMAL] (the pane ranks below the host).
 */
object PipeFullScreen {
    fun open(
        activity: ComponentActivity,
        provider: ProviderComponent,
        request: PipeRequest,
        authorizer: PipeAuthorizer = PipeAuthorizers.sameSigningKey(activity),
        bindImportance: PipeBindImportance = PipeBindImportance.NORMAL,
        onSession: (PipeSession) -> Unit = {},
        onError: (PipeException) -> Unit = {},
    ): Job {
        val connection = PipeConnection(activity, hostToken = { activity.window.decorView.windowToken })

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
        }

        return activity.lifecycleScope.launch {
            try {
                val session = connection.open(provider, request, authorizer, 10.seconds, bindImportance)
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
