package tech.ssemaj.pipe.evilhost

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import tech.ssemaj.pipe.auth.AuthDecision
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.core.CloseReason
import tech.ssemaj.pipe.core.PipeError
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.host.PipeHostCallbacks
import tech.ssemaj.pipe.host.PipeSession
import tech.ssemaj.pipe.host.PipeView
import tech.ssemaj.pipe.host.ProviderComponent

class EvilMainActivity : AppCompatActivity() {
    private var session: PipeSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val status = findViewById<TextView>(R.id.host_status)
        val pipeView = findViewById<PipeView>(R.id.pipe_view)

        findViewById<Button>(R.id.send_button).setOnClickListener {
            session?.send(PipeMessage(bundleOf("text" to "hello-from-host")))
        }

        val provider = ProviderComponent(
            packageName = "tech.ssemaj.pipe.sampleprovider",
            serviceClass = "tech.ssemaj.pipe.sampleprovider.DemoPaneService",
        )
        session = pipeView.open(
            provider = provider,
            request = PipeRequest("demo.editor"),
            // Allow-all: this test isolates the PROVIDER-side gate, not the host's own policy.
            authorizer = PipeAuthorizer { _, _ -> AuthDecision.Allow },
            callbacks = object : PipeHostCallbacks {
                override fun onOpened(session: PipeSession) { status.text = "opened" }
                override fun onMessage(message: PipeMessage) { status.text = "msg: ${message.payload.getString("text")}" }
                override fun onDenied(reason: String) { status.text = "denied: $reason" }
                override fun onError(error: PipeError) { status.text = "error: ${error.code}" }
                override fun onClosed(reason: CloseReason) { status.text = "closed: $reason" }
            },
        )
    }
}
