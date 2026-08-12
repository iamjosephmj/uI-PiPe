package tech.ssemaj.pipe.samplehost

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import tech.ssemaj.pipe.auth.AuthDecision
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.auth.PipeAuthorizers
import tech.ssemaj.pipe.core.PipeException
import tech.ssemaj.pipe.core.PipeDeniedException
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.core.PipeState
import tech.ssemaj.pipe.host.PipeSession
import tech.ssemaj.pipe.host.PipeView
import tech.ssemaj.pipe.host.ProviderComponent

class MainActivity : AppCompatActivity() {
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
            packageName = intent.getStringExtra("targetPackage") ?: "tech.ssemaj.pipe.sampleprovider",
            serviceClass = intent.getStringExtra("targetService") ?: "tech.ssemaj.pipe.sampleprovider.DemoPaneService",
        )
        val authorizer: PipeAuthorizer = when (intent.getStringExtra("targetAuthorizer")) {
            "suspend-deny" -> PipeAuthorizer { _, _ ->
                yield()
                AuthDecision.Deny("async-policy")
            }
            else -> PipeAuthorizers.sameSigningKey(this)
        }

        lifecycleScope.launch {
            try {
                val s = pipeView.open(provider, PipeRequest("demo.editor"), authorizer)
                session = s
                status.text = "opened"
                launch {
                    s.messages.collect { status.text = "msg: ${it.payload.getString("text")}" }
                }
                launch {
                    s.state.collect {
                        if (it is PipeState.Closed) {
                            status.text = "closed: ${it.cause?.let { c -> c::class.simpleName } ?: "clean"}"
                        }
                    }
                }
            } catch (e: PipeDeniedException) {
                status.text = "denied: ${e.reason}"
            } catch (e: PipeException) {
                status.text = "error: ${e::class.simpleName}"
            }
        }
    }
}
