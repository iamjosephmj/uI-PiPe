package tech.ssemaj.pipe.provider

import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Adds [root] as the pane's full-screen window, parented to the host's window token, per [spec].
 *
 * This is where Pipe's whole mechanism lives: a `TYPE_APPLICATION_PANEL` window whose
 * [WindowManager.LayoutParams.token] is the host activity's window token ([hostToken]) becomes a
 * child window of the host's window in the same task — a genuine window in the host's hierarchy, and
 * therefore a first-class focus/IME/input target on every API from 30 up, with no
 * `SurfaceControlViewHost` and no `@hide` APIs.
 *
 * The window is always full-screen; [spec] only decides opacity ([PaneSpec.translucent], default
 * transparent) and focusability. Any dialog/sheet shape is the provider's own drawing inside this
 * canvas. `SOFT_INPUT_ADJUST_RESIZE` keeps pane editors visible above the soft keyboard.
 */
internal fun WindowManager.addPane(root: View, hostToken: IBinder, spec: PaneSpec) {
    val flags = if (spec.focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
        flags,
        if (spec.translucent) PixelFormat.TRANSLUCENT else PixelFormat.OPAQUE,
    ).apply {
        token = hostToken
        gravity = Gravity.TOP or Gravity.START
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }
    addView(root, params)
}
