package tech.ssemaj.pipe.samplesolo

import android.app.Application
import android.graphics.Color
import android.os.Process
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.provider.HostHandle
import tech.ssemaj.pipe.provider.PaneResult
import tech.ssemaj.pipe.provider.PaneSpec
import tech.ssemaj.pipe.provider.PipeContent
import tech.ssemaj.pipe.provider.PipeProviderService

/**
 * Experiment: N processes, each **independently interactive**, in one host window. Each subclass runs
 * in its own process and draws a band anchored to [gravity]. The pane window is sized to the band and
 * made non-touch-modal ([PaneSpec.touchModal] = false), so touches outside the band pass through to
 * the panes/host behind it — every band's button works, not just the top one's. Each band prints its
 * UI-thread name + kernel tid so you can see the threads (and processes) are genuinely distinct.
 */
abstract class RegionPaneService : PipeProviderService() {
    abstract val label: String
    abstract val gravity: Int
    abstract val color: Int

    override suspend fun onOpenPane(request: PipeRequest, host: HostHandle): PaneResult {
        var taps = 0

        val title = TextView(this).apply {
            text = "$label   ·   ${Application.getProcessName()}   ·   pid ${Process.myPid()}"
            textSize = 15f; setTextColor(Color.WHITE)
        }
        val status = TextView(this).apply {
            text = "UI thread “${Thread.currentThread().name}” · tid ${Process.myTid()}"
            textSize = 12f; setTextColor(Color.WHITE)
        }
        val button = Button(this).apply {
            text = "Tap $label"
            setOnClickListener {
                taps++
                status.text = "tapped ${taps}× · UI thread “${Thread.currentThread().name}” · tid ${Process.myTid()}"
            }
        }
        val band = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(color)
            setPadding(40, 28, 40, 28)
            addView(title); addView(status); addView(button)
        }

        // Window sized to this band, non-touch-modal → independently interactive; touches elsewhere
        // fall through to the other panes / the host.
        val spec = PaneSpec(
            focusable = false,
            touchModal = false,
            gravity = gravity,
            heightPx = WindowManager.LayoutParams.WRAP_CONTENT,
        )
        return PaneResult.Content(object : PipeContent { override val view: View = band }, spec)
    }
}

class PaneServiceA : RegionPaneService() {
    override val label = "Pane A"; override val gravity = Gravity.TOP; override val color = Color.parseColor("#2563EB")
}

class PaneServiceB : RegionPaneService() {
    override val label = "Pane B"; override val gravity = Gravity.CENTER_VERTICAL; override val color = Color.parseColor("#7C3AED")
}

class PaneServiceC : RegionPaneService() {
    override val label = "Pane C"; override val gravity = Gravity.BOTTOM; override val color = Color.parseColor("#059669")
}
