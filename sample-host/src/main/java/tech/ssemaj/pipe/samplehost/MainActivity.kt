package tech.ssemaj.pipe.samplehost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.yield
import tech.ssemaj.pipe.auth.AuthDecision
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.auth.PipeAuthorizers
import tech.ssemaj.pipe.host.ProviderComponent
import tech.ssemaj.pipe.samplehost.presentation.CertificationViewModel
import tech.ssemaj.pipe.samplehost.presentation.ui.CertificationScreen
import tech.ssemaj.pipe.samplehost.presentation.ui.PipeDemoTheme

class MainActivity : ComponentActivity() {

    private val viewModel: CertificationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        setContent {
            PipeDemoTheme {
                val state by viewModel.uiState.collectAsState()
                CertificationScreen(
                    state = state,
                    onPaneViewCreated = { view -> viewModel.onPaneViewReady(view, provider, authorizer) },
                    onRequest = { viewModel.requestCertification(hostDisplayName = "Pipe Sample Host") },
                    onReopen = viewModel::reopen,
                )
            }
        }
    }
}
