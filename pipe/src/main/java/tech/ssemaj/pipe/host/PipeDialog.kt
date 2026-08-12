package tech.ssemaj.pipe.host

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.auth.PipeAuthorizers
import tech.ssemaj.pipe.core.PipeException
import tech.ssemaj.pipe.core.PipePresentation
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.core.PipeState
import tech.ssemaj.pipe.core.forcePresentation

/**
 * Shows a pane inside a dimmed dialog. Dismiss (scrim tap/back) closes the session cleanly.
 *
 * Implemented as a scrim + card overlay added directly to [activity]'s own content view — *not*
 * a separate [android.app.Dialog] window. A pane's [PipeView] renders via an embedded
 * SurfaceControlViewHost; hosting that inside a genuinely separate window breaks two things a
 * real system Dialog would otherwise give for free: (1) the dialog window never picks up input
 * focus once the pane's surface takes `setZOrderOnTop` + a direct input-transfer token, so BACK
 * lands on the Activity underneath instead of the dialog; and (2) — more fundamentally — the
 * embedded surface's accessibility node tree does not bridge up through a second window, so
 * on-device UI text checks (and real accessibility services) can't see the pane's content at
 * all, even though it renders and accepts touches correctly. Neither is fixable via window flags;
 * both are avoided by keeping the pane at the same window-nesting depth full-screen panes use.
 */
class PipeDialog private constructor(
    private val scrim: ViewGroup,
    private val backCallback: OnBackPressedCallback,
) {
    private var openSession: PipeSession? = null
    private var onDismiss: () -> Unit = {}
    private var dismissed = false

    fun dismiss() {
        if (dismissed) return
        dismissed = true
        backCallback.isEnabled = false
        backCallback.remove()
        (scrim.parent as? ViewGroup)?.removeView(scrim)
        openSession?.close()
        onDismiss()
    }

    companion object {
        fun show(
            activity: ComponentActivity,
            provider: ProviderComponent,
            request: PipeRequest,
            authorizer: PipeAuthorizer = PipeAuthorizers.sameSigningKey(activity),
            onSession: (PipeSession) -> Unit = {},
            onError: (PipeException) -> Unit = {},
            onDismiss: () -> Unit = {},
        ): PipeDialog {
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            val pipeView = PipeView(activity)
            val dm = activity.resources.displayMetrics
            val m = (16 * dm.density).toInt()
            val radius = 16 * dm.density
            val card = FrameLayout(activity).apply {
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = radius
                }
                clipToOutline = true
                setPadding(m, m, m, m)
                addView(pipeView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
            val scrim = FrameLayout(activity).apply {
                background = ColorDrawable(Color.argb(153, 0, 0, 0))
                isClickable = true
                addView(card, FrameLayout.LayoutParams(
                    (dm.widthPixels * 0.92f).toInt(), (dm.heightPixels * 0.62f).toInt(), Gravity.CENTER))
            }

            lateinit var handle: PipeDialog
            val backCallback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = handle.dismiss()
            }
            activity.onBackPressedDispatcher.addCallback(activity, backCallback)

            handle = PipeDialog(scrim, backCallback)
            handle.onDismiss = onDismiss
            // Tapping the scrim outside the card dismisses, matching a normal dialog's
            // cancel-on-touch-outside default. The card itself consumes its own clicks.
            scrim.setOnClickListener { handle.dismiss() }
            card.isClickable = true

            root.addView(scrim, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

            pipeView.openIn(
                owner = activity,
                provider = provider,
                request = request.forcePresentation(PipePresentation.DIALOG),
                authorizer = authorizer,
                onError = { e -> handle.dismiss(); onError(e) },
                onSession = { session ->
                    handle.openSession = session
                    // The session can end other ways than scrim/back (host-initiated close,
                    // provider close, peer death) — tear the overlay down then too.
                    activity.lifecycleScope.launch {
                        session.state.first { it is PipeState.Closed }
                        handle.dismiss()
                    }
                    onSession(session)
                },
            )
            return handle
        }
    }
}
