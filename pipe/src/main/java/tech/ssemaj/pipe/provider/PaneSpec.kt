package tech.ssemaj.pipe.provider

/**
 * Window-level properties of the pane — only the things the provider genuinely *can't* set from its
 * own content. The pane is always a full-screen window parented to the host's window token; a
 * dialog, a bottom sheet, a scrim, rounded corners are **not** modelled here — the provider draws
 * them inside the (transparent) full-screen canvas and animates them itself (Compose or View
 * animations).
 *
 * Security note: a transparent pane leaves host content visible behind the provider's own drawing
 * while still being touch-modal — a larger surface than an opaque takeover. Only hand a provider
 * this inside a trusted, signing-verified app family.
 */
data class PaneSpec(
    /** Translucent pixels, so the provider can draw partial UI with the host visible behind it.
     *  `false` = a fully opaque full-screen window. */
    val translucent: Boolean = true,
    /** Focusable, so the pane is an IME/key target. Only turn off for a passive, non-interactive pane. */
    val focusable: Boolean = true,
)
