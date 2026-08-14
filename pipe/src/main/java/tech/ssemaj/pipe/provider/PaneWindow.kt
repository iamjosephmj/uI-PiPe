package tech.ssemaj.pipe.provider

import android.graphics.PixelFormat
import android.os.IBinder
import android.view.View
import android.view.WindowManager

/**
 * Adds [root] as the pane's window, parented to the host's window token, per [spec].
 *
 * This is where Pipe's whole mechanism lives: a `TYPE_APPLICATION_PANEL` window whose
 * [WindowManager.LayoutParams.token] is the host activity's window token ([hostToken]) becomes a
 * child window of the host's window in the same task — a genuine window in the host's hierarchy, and
 * therefore a first-class focus/IME/input target on every API from 30 up, with no
 * `SurfaceControlViewHost` and no `@hide` APIs.
 *
 * By default the window is full-screen, focusable, translucent, and touch-modal; [spec] can shrink
 * it to a region and make it non-touch-modal (for tiling / multi-pane). `SOFT_INPUT_ADJUST_RESIZE`
 * keeps pane editors visible above the soft keyboard.
 */
internal fun WindowManager.addPane(root: View, hostToken: IBinder, spec: PaneSpec) {
    var flags = 0
    if (!spec.focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    if (!spec.touchModal) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

    val params = WindowManager.LayoutParams(
        spec.widthPx,
        spec.heightPx,
        WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
        flags,
        if (spec.translucent) PixelFormat.TRANSLUCENT else PixelFormat.OPAQUE,
    ).apply {
        token = hostToken
        gravity = spec.gravity
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }
    addView(root, params)
}
