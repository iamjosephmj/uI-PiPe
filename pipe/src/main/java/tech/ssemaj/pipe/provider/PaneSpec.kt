package tech.ssemaj.pipe.provider

import android.view.Gravity
import android.view.WindowManager

/**
 * Window-level properties of the pane — the things the provider can't set from its own content.
 * The default is a full-screen transparent, focusable, touch-modal window (the common case).
 *
 * The geometry fields ([gravity]/[widthPx]/[heightPx]) plus [touchModal] exist for **tiling /
 * multi-pane**: a window sized to a region, with `touchModal = false`, only consumes touches within
 * its own bounds and lets touches outside pass through to whatever is behind it (the host, or
 * another pane). Several such non-overlapping panes are each independently interactive. This is
 * *not* a "dialog" API — a dialog is still just something the provider draws inside a full-screen
 * pane; this is genuine window geometry.
 *
 * Security note: a transparent or partial pane leaves host content visible while still capturing
 * input over its bounds — only hand a provider this inside a trusted, signing-verified app family.
 */
data class PaneSpec(
    /** Translucent pixels, so the provider can draw partial UI with the host visible behind it. */
    val translucent: Boolean = true,
    /** Focusable, so the pane is an IME/key target. `false` also implies non-touch-modal. */
    val focusable: Boolean = true,
    /**
     * `true` (default): the pane captures all touches over the screen. `false`
     * (`FLAG_NOT_TOUCH_MODAL`): it captures touches only within its own bounds and passes the rest
     * through to whatever is behind — the basis for independently-interactive, non-overlapping panes.
     */
    val touchModal: Boolean = true,
    val gravity: Int = Gravity.TOP or Gravity.START,
    val widthPx: Int = WindowManager.LayoutParams.MATCH_PARENT,
    val heightPx: Int = WindowManager.LayoutParams.MATCH_PARENT,
    /**
     * Full-bleed pane: **no automatic insets padding** on the pane root, so a scrim covers the
     * pane's whole window edge-to-edge. With the default `false` the pane content is inset to the
     * system bars. When `true`, handle insets yourself in the content (in Compose:
     * `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` on the actual card, *after* the
     * full-bleed scrim).
     *
     * Note: a pane is a sub-window of the host and therefore cannot itself draw over the status
     * and navigation bar strips — pair this with the host-side
     * `PipeFullScreen.open(dimSystemBars = true)` to dim those strips to match the scrim.
     */
    val edgeToEdge: Boolean = false,
)
