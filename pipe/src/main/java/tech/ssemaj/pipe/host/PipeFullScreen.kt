package tech.ssemaj.pipe.host

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.auth.PipeAuthorizers
import tech.ssemaj.pipe.core.PipeException
import tech.ssemaj.pipe.core.PipePresentation
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.core.PipeState
import tech.ssemaj.pipe.core.forcePresentation

/** Opens a pane that fills [activity]'s content area. Back, session close (from either side, or
 *  peer death), or an open error removes the container — whichever happens first. */
object PipeFullScreen {
    fun open(
        activity: ComponentActivity,
        provider: ProviderComponent,
        request: PipeRequest,
        authorizer: PipeAuthorizer = PipeAuthorizers.sameSigningKey(activity),
        onSession: (PipeSession) -> Unit = {},
        onError: (PipeException) -> Unit = {},
    ): Job {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val pipeView = PipeView(activity)
        root.addView(pipeView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                pipeView.close()
                remove(root, pipeView, this)
            }
        }
        activity.onBackPressedDispatcher.addCallback(activity, backCallback)

        return pipeView.openIn(
            owner = activity,
            provider = provider,
            request = request.forcePresentation(PipePresentation.FULL_SCREEN),
            authorizer = authorizer,
            onError = { e -> remove(root, pipeView, backCallback); onError(e) },
            onSession = { session ->
                // The session can end other ways than back-press (host-initiated close, provider
                // close, peer death) — tear the container down then too, so it never leaks.
                activity.lifecycleScope.launch {
                    session.state.collect { s ->
                        if (s is PipeState.Closed) remove(root, pipeView, backCallback)
                    }
                }
                onSession(session)
            },
        )
    }

    /** Idempotent: back-press and the session-state observer can both fire for the same close. */
    private fun remove(root: ViewGroup, view: PipeView, cb: OnBackPressedCallback) {
        if (!cb.isEnabled) return
        cb.isEnabled = false
        cb.remove()
        (view.parent as? ViewGroup)?.removeView(view)
    }
}
