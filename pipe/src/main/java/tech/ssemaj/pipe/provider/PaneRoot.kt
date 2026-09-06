package tech.ssemaj.pipe.provider

import android.content.Context
import android.os.Build
import android.view.KeyEvent
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Root container of a pane's window. It owns three window-level concerns so the provider's own
 * content doesn't have to:
 *  - **Insets** — unless the pane is [PaneSpec.edgeToEdge], pads itself by the system-bar insets,
 *    so an edge-to-edge host never pushes pane content under the status or navigation bars.
 *  - **Back** — the pane's window is focusable, so it receives the BACK key; [PaneRoot] turns a
 *    BACK press into a provider-side dismissal via [onBack].
 *  - **Compose readiness** — as the window's root view it is a `LifecycleOwner` /
 *    `SavedStateRegistryOwner` / `ViewModelStoreOwner` and installs itself as the ViewTree owner, so
 *    a provider can drop a `ComposeView` (or any lifecycle-aware view) straight in, even though the
 *    window is added from a Service with no Activity. Attached → `RESUMED`; detached → `DESTROYED`,
 *    which disposes the composition cleanly.
 *
 * [onBack] is wired by [PipeProviderService] once the pane is live; until then a BACK press is a no-op.
 *
 * @param fitSystemBars pad the root by the system-bar insets (the default pane behavior). `false`
 *   for [PaneSpec.edgeToEdge] panes, which draw under the bars — insets still dispatch to content.
 */
internal class PaneRoot(
    context: Context,
    fitSystemBars: Boolean = true,
) : FrameLayout(context), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    /** Invoked on a BACK key-up while this window has focus. Set by the service after the pane opens. */
    var onBack: (() -> Unit)? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    init {
        isFocusableInTouchMode = true
        savedState.performRestore(null)
        setViewTreeLifecycleOwner(this)
        setViewTreeViewModelStoreOwner(this)
        setViewTreeSavedStateRegistryOwner(this)
        setOnApplyWindowInsetsListener { v, insets ->
            if (fitSystemBars) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    v.setPadding(
                        insets.systemWindowInsetLeft,
                        insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight,
                        insets.systemWindowInsetBottom,
                    )
                }
            }
            insets // never consume — content (e.g. Compose) can still read the insets it needs
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDetachedFromWindow()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            onBack?.invoke()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
