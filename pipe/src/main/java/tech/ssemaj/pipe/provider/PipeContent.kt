package tech.ssemaj.pipe.provider

import android.view.View
import tech.ssemaj.pipe.core.CloseReason
import tech.ssemaj.pipe.core.PipeMessage

/**
 * What the provider app renders into its pane. Return an implementation of this from
 * [onOpenPane][PipeProviderService.onOpenPane] inside [PaneResult.Content].
 *
 * All callbacks arrive on the provider's main thread. [onMessage]/[onClosed] are optional —
 * a fire-and-forget pane can implement only [view].
 */
interface PipeContent {
    /** The pane's UI. The library adds it to the pane window as-is; animate entrances yourself. */
    val view: View

    /** A message arrived from the host. */
    fun onMessage(message: PipeMessage) {}

    /** The pane came down ([reason] says which side closed, or that the host died). */
    fun onClosed(reason: CloseReason) {}
}
