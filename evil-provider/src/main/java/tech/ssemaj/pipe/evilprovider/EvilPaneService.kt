package tech.ssemaj.pipe.evilprovider

import android.view.View
import android.widget.TextView
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.provider.HostHandle
import tech.ssemaj.pipe.provider.PaneResult
import tech.ssemaj.pipe.provider.PipeContent
import tech.ssemaj.pipe.provider.PipeProviderService

class EvilPaneService : PipeProviderService() {

    override suspend fun onOpenPane(request: PipeRequest, host: HostHandle): PaneResult {
        val view = TextView(this).apply { text = "EVIL-PANE" }
        return PaneResult.Content(object : PipeContent {
            override val view: View = view
            override fun onMessage(message: PipeMessage) {}
        })
    }
}
