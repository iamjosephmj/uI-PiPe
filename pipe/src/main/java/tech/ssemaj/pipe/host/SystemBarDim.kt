package tech.ssemaj.pipe.host

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator

/**
 * Dims the host's own status- and navigation-bar strips while a full-bleed pane is open.
 *
 * Why this exists: a pane is a sub-window of the host's window, and the window manager constrains
 * sub-windows to the parent's content frame — the pane's scrim physically cannot cover the bar
 * strips. Those strips show the *host's* pixels (the host window itself is full-screen when
 * edge-to-edge), so the host dims them instead: two plain scrim strips are added over the host's
 * decor, animated to the same 70% black a pane scrim typically uses. The strips are inert —
 * non-clickable views don't consume touches, so gestures and taps pass through untouched.
 */
internal class SystemBarDim(private val activity: Activity) {

    private val decor: ViewGroup get() = activity.window.decorView as ViewGroup
    private var strips: MutableList<View>? = null

    fun attach() {
        if (strips != null) return
        val insets = decor.rootWindowInsets ?: return
        val topInset = insets.getInsets(WindowInsets.Type.statusBars()).top
        val bottomInset = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        if (topInset <= 0 && bottomInset <= 0) return

        val added = mutableListOf<View>()
        if (topInset > 0) added += strip(heightPx = topInset, topMargin = 0)
        if (bottomInset > 0) added += strip(heightPx = bottomInset, topMargin = decor.height - bottomInset)
        strips = added
    }

    fun detach() {
        val current = strips ?: return
        strips = null
        current.forEach { strip ->
            strip.animate()
                .alpha(0f)
                .setDuration(DURATION_OUT_MS)
                .setInterpolator(DecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        (strip.parent as? ViewGroup)?.removeView(strip)
                    }
                })
                .start()
        }
    }

    private fun strip(heightPx: Int, topMargin: Int): View {
        val strip = View(decor.context)
        strip.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        strip.setBackgroundColor(SCRIM_COLOR.toInt())
        strip.alpha = 0f
        decor.addView(
            strip,
            ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx).apply {
                this.topMargin = topMargin
            },
        )
        strip.animate()
            .alpha(1f)
            .setDuration(DURATION_IN_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
        return strip
    }

    private companion object {
        /** Matches the conventional pane scrim (70% black). */
        const val SCRIM_COLOR = 0xB3000000L
        const val DURATION_IN_MS = 300L
        const val DURATION_OUT_MS = 220L
    }
}
