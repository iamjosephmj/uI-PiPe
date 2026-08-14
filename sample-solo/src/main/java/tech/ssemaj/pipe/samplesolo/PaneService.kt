package tech.ssemaj.pipe.samplesolo

import android.app.Application
import android.graphics.Color
import android.os.Process
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.provider.HostHandle
import tech.ssemaj.pipe.provider.PaneResult
import tech.ssemaj.pipe.provider.PipeContent
import tech.ssemaj.pipe.provider.PipeProviderService

/**
 * The pane, running in this app's `:pane` process (see the manifest). It draws the host's and its own
 * process name + pid so you can *see* they differ — same app, same UID, two processes, one window.
 */
class PaneService : PipeProviderService() {

    override suspend fun onOpenPane(request: PipeRequest, host: HostHandle): PaneResult {
        val hostPid = request.extras.getInt("hostPid", -1)
        val hostProc = request.extras.getString("hostProc") ?: "?"

        fun line(text: String, sizeSp: Float, color: Int) = TextView(this).apply {
            this.text = text; textSize = sizeSp; setTextColor(color)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0D141D"))
            setPadding(72, 72, 72, 72)
            addView(line("Pane rendered from a separate process", 20f, Color.WHITE))
            addView(line("\nHost   ·  $hostProc\n            pid $hostPid", 15f, Color.parseColor("#58A6FF")))
            addView(line("\nPane   ·  ${Application.getProcessName()}\n            pid ${Process.myPid()}", 15f, Color.parseColor("#A371F7")))
            addView(line("\nSame app · same UID (${Process.myUid()}) · two processes · one window", 13f, Color.parseColor("#8B98A5")))
            addView(Button(this@PaneService).apply { text = "Close"; setOnClickListener { host.close() } })
        }
        return PaneResult.Content(object : PipeContent { override val view: View = root })
    }
}
