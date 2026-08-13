package tech.ssemaj.pipe.provider

import android.content.Context
import android.view.KeyEvent
import android.view.WindowInsets
import android.widget.FrameLayout

/**
 * Root container of a pane's window.
 *
 * It owns two window-level concerns so the provider's own content doesn't have to:
 *  - **Insets** — pads itself by the system-bar insets, so an edge-to-edge host never pushes pane
 *    content under the status or navigation bars.
 *  - **Back** — the pane's window is focusable (a real IME/input target), so it receives the BACK
 *    key; [PaneRoot] turns a BACK press into a provider-side dismissal via [onBack].
 *
 * [onBack] is wired by [PipeProviderService] once the pane is live; until then a BACK press is a
 * no-op.
 */
internal class PaneRoot(context: Context) : FrameLayout(context) {

    /** Invoked on a BACK key-up while this window has focus. Set by the service after the pane opens. */
    var onBack: (() -> Unit)? = null

    init {
        isFocusableInTouchMode = true
        setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            onBack?.invoke()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
