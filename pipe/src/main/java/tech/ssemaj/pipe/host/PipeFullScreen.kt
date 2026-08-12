package tech.ssemaj.pipe.host

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import kotlinx.coroutines.Job
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.auth.PipeAuthorizers
import tech.ssemaj.pipe.core.PipeException
import tech.ssemaj.pipe.core.PipePresentation
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.core.forcePresentation

/** Opens a pane that fills [activity]'s content area. Back or session close removes the container. */
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
            onSession = { session -> onSession(session) },
        )
    }

    private fun remove(root: ViewGroup, view: PipeView, cb: OnBackPressedCallback) {
        cb.isEnabled = false
        cb.remove()
        (view.parent as? ViewGroup)?.removeView(view)
    }
}
