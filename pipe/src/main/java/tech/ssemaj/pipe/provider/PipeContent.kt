package tech.ssemaj.pipe.provider

import android.view.View
import tech.ssemaj.pipe.core.CloseReason
import tech.ssemaj.pipe.core.PipeMessage

/** What the provider app renders into its full-screen pane. All callbacks arrive on the main thread. */
interface PipeContent {
    val view: View
    fun onMessage(message: PipeMessage) {}
    fun onClosed(reason: CloseReason) {}
}
