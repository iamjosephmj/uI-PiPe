package tech.ssemaj.pipe.evilhost

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import tech.ssemaj.pipe.auth.AuthDecision
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.core.PipeDeniedException
import tech.ssemaj.pipe.core.PipeException
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeRequest
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
            lifecycleScope.launch { session?.send(PipeMessage(bundleOf("text" to "hello-from-host"))) }
        }

        val provider = ProviderComponent(
            packageName = "tech.ssemaj.pipe.sampleprovider",
            serviceClass = "tech.ssemaj.pipe.sampleprovider.DemoPaneService",
        )
        lifecycleScope.launch {
            try {
                val s = pipeView.open(
                    provider = provider,
                    request = PipeRequest("demo.editor"),
                    // Allow-all: this test isolates the PROVIDER-side gate, not the host's own policy.
                    authorizer = PipeAuthorizer { _, _ -> AuthDecision.Allow },
                )
                session = s
                status.text = "opened"
                s.messages.collect { status.text = "msg: ${it.payload.getString("text")}" }
            } catch (e: PipeDeniedException) {
                status.text = "denied: ${e.reason}"
            } catch (e: PipeException) {
                status.text = "error: ${e::class.simpleName}"
            }
        }
    }
}
