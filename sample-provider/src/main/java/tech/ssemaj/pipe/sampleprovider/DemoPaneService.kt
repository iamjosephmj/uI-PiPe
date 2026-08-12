package tech.ssemaj.pipe.sampleprovider

import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.provider.HostHandle
import tech.ssemaj.pipe.provider.PipeContent
import tech.ssemaj.pipe.provider.PipeProviderService

class DemoPaneService : PipeProviderService() {

    override fun onOpenPane(request: PipeRequest, host: HostHandle): PipeContent {
        val editText = EditText(this).apply { hint = "type here" }
        val status = TextView(this).apply { text = "pane-ready" }
        val button = Button(this).apply {
            text = "Ping Host"
            setOnClickListener {
                host.send(PipeMessage(bundleOf("type" to "ping", "text" to editText.text.toString())))
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            addView(status)
            addView(editText)
            addView(button)
        }
        return object : PipeContent {
            override val view: View = root
            override fun onMessage(message: PipeMessage) {
                status.text = message.payload.getString("text") ?: "(no text)"
            }
        }
    }
}
