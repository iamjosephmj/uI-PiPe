package tech.ssemaj.pipe.samplesolo

import android.app.Application
import android.graphics.Color
import android.os.Process
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.provider.HostHandle
import tech.ssemaj.pipe.provider.PaneResult
import tech.ssemaj.pipe.provider.PipeContent
import tech.ssemaj.pipe.provider.PipeProviderService

/**
 * Experiment: N processes sharing one window. Each subclass runs in its **own** process and draws a
 * single coloured band into its transparent full-screen pane, anchored to [gravity]. Open all three
 * from the host and the bands compose in one host window — proving the host isn't limited to a single
 * provider process. (Only the top-most pane is the input target; this is a visual proof.)
 */
abstract class RegionPaneService : PipeProviderService() {
    abstract val label: String
    abstract val gravity: Int
    abstract val color: Int

    override suspend fun onOpenPane(request: PipeRequest, host: HostHandle): PaneResult {
        val band = TextView(this).apply {
            text = "$label   ·   ${Application.getProcessName()}   ·   pid ${Process.myPid()}"
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(color)
            setPadding(48, 44, 48, 44)
            this.gravity = Gravity.CENTER
        }
        val root = FrameLayout(this).apply {
            addView(band, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                this@RegionPaneService.gravity,
            ))
        }
        return PaneResult.Content(object : PipeContent { override val view: View = root })
    }
}

class PaneServiceA : RegionPaneService() {
    override val label = "Pane A"; override val gravity = Gravity.TOP; override val color = Color.parseColor("#2563EB")
}

class PaneServiceB : RegionPaneService() {
    override val label = "Pane B"; override val gravity = Gravity.CENTER; override val color = Color.parseColor("#7C3AED")
}

class PaneServiceC : RegionPaneService() {
    override val label = "Pane C"; override val gravity = Gravity.BOTTOM; override val color = Color.parseColor("#059669")
}
