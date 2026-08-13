package tech.ssemaj.pipe.provider

import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Adds [root] as the pane's full-screen window, parented to the host's window token.
 *
 * This one call is where Pipe's whole cross-process mechanism lives: a `TYPE_APPLICATION_PANEL`
 * window whose [WindowManager.LayoutParams.token] is the host activity's window token ([hostToken])
 * becomes a child window of the host's window in the same task — a genuine window in the host's
 * hierarchy, and therefore a first-class focus/IME/input target on every API from 30 up, with no
 * `SurfaceControlViewHost` and no `@hide` APIs.
 *
 *  - `MATCH_PARENT × MATCH_PARENT` + `Gravity.TOP|START` — fill the host window; [PaneRoot] handles
 *    the system-bar insets.
 *  - `SOFT_INPUT_ADJUST_RESIZE` — resize for the soft keyboard so pane editors stay visible.
 *  - `PixelFormat.OPAQUE` — the pane fully covers the host, no compositing behind it.
 */
internal fun WindowManager.addPane(root: View, hostToken: IBinder) {
    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
        0,
        PixelFormat.OPAQUE,
    ).apply {
        token = hostToken
        gravity = Gravity.TOP or Gravity.START
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }
    addView(root, params)
}
