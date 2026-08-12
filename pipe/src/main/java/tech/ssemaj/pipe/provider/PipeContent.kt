package tech.ssemaj.pipe.provider

import android.view.View
import tech.ssemaj.pipe.core.CloseReason
import tech.ssemaj.pipe.core.PipeMessage

/** What the provider app renders into the host. All callbacks arrive on the main thread. */
interface PipeContent {
    val view: View
    fun onMessage(message: PipeMessage) {}
    fun onResized(widthPx: Int, heightPx: Int) {}
    fun onClosed(reason: CloseReason) {}
}
