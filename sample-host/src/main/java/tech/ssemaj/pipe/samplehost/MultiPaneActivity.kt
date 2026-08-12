package tech.ssemaj.pipe.samplehost

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tech.ssemaj.pipe.core.PipeException
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.host.PipeView
import tech.ssemaj.pipe.host.ProviderComponent
import tech.ssemaj.pipe.samples.contract.ACTION_CERTIFICATION
import tech.ssemaj.pipe.samplehost.di.AppContainer
import tech.ssemaj.pipe.samplehost.domain.RequestCertificationUseCase

/** Two panes on one provider — exists for MultiPaneE2eTest, intentionally plain. */
class MultiPaneActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val container = AppContainer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multi_pane)
        val provider = ProviderComponent(
            packageName = "tech.ssemaj.pipe.sampleprovider",
            serviceClass = "tech.ssemaj.pipe.sampleprovider.DemoPaneService",
        )
        runPane(findViewById(R.id.pane_a), findViewById(R.id.status_a), "A", provider)
        runPane(findViewById(R.id.pane_b), findViewById(R.id.status_b), "B", provider)
    }

    private fun runPane(pane: PipeView, status: TextView, label: String, provider: ProviderComponent) {
        scope.launch {
            try {
                val session = pane.open(provider, PipeRequest(ACTION_CERTIFICATION))
                status.text = "$label: connected"
                when (val outcome = container.requestCertification(session, "Multi-Pane Host $label")) {
                    is RequestCertificationUseCase.Outcome.NeedsVerification -> {
                        val result = container.verifyCertification(outcome.nonce, outcome.granted)
                        status.text = if (result.verified) "$label: verified" else "$label: verification failed"
                    }
                    is RequestCertificationUseCase.Outcome.Declined -> status.text = "$label: declined"
                    RequestCertificationUseCase.Outcome.Timeout -> status.text = "$label: timeout"
                }
            } catch (e: PipeException) {
                status.text = "$label: error ${e::class.simpleName}"
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
