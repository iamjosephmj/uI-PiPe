package tech.ssemaj.pipe.evilhost

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import tech.ssemaj.pipe.auth.AuthDecision
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.core.PipeDeniedException
import tech.ssemaj.pipe.core.PipeException
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.host.PipeFullScreen
import tech.ssemaj.pipe.host.ProviderComponent

/**
 * A differently-signed host. It runs an allow-all HOST authorizer so it isolates the PROVIDER-side
 * gate: the provider must refuse the differently-signed caller. On denial the pane never renders.
 */
class EvilMainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val status = findViewById<TextView>(R.id.host_status)

        val provider = ProviderComponent(
            packageName = "tech.ssemaj.pipe.sampleprovider",
            serviceClass = "tech.ssemaj.pipe.sampleprovider.DemoPaneService",
        )
        PipeFullScreen.open(
            activity = this,
            provider = provider,
            request = PipeRequest("demo.editor"),
            // Allow-all: this isolates the PROVIDER-side gate, not the host's own policy.
            authorizer = PipeAuthorizer { _, _ -> AuthDecision.Allow },
            onSession = { status.text = "opened" },
            onError = { e ->
                status.text = when (e) {
                    is PipeDeniedException -> "denied: ${e.reason}"
                    else -> "error: ${e::class.simpleName}"
                }
            },
        )
    }
}
