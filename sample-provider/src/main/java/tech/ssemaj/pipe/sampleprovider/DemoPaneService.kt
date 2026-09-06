package tech.ssemaj.pipe.sampleprovider

import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import kotlinx.coroutines.launch
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.provider.HostHandle
import tech.ssemaj.pipe.provider.PaneResult
import tech.ssemaj.pipe.provider.PaneSpec
import tech.ssemaj.pipe.provider.PipeContent
import tech.ssemaj.pipe.provider.PipeProviderService
import tech.ssemaj.pipe.samples.contract.CertificationRequest
import tech.ssemaj.pipe.samples.contract.CertificationResponse
import tech.ssemaj.pipe.sampleprovider.data.KeystoreRepository
import tech.ssemaj.pipe.sampleprovider.domain.IssueCertificationUseCase
import tech.ssemaj.pipe.sampleprovider.pane.ConsentDialog
import tech.ssemaj.pipe.sampleprovider.pane.PanePresenter
import tech.ssemaj.pipe.serialization.PipeCodec
import tech.ssemaj.pipe.serialization.send

/**
 * Sample provider: renders the consent flow as a **Compose dialog drawn inside the pane**.
 *
 * The pane is the library's default — a full-screen transparent window — and everything the user
 * sees (scrim, centered card, buttons, dismiss) is composed here in [ConsentDialog]; the
 * library knows nothing about "dialog". `ComposeView` works with nothing extra because the pane's
 * `PaneRoot` is a lifecycle/saved-state/viewmodel owner, so a Compose pane just drops in.
 */
class DemoPaneService : PipeProviderService() {

    private val issueCertification = IssueCertificationUseCase(KeystoreRepository())

    override suspend fun onOpenPane(request: PipeRequest, host: HostHandle): PaneResult {
        val presenter = PanePresenter()
        var lastRequest: CertificationRequest? = null

        val composeView = ComposeView(this).apply {
            setContent {
                val state by presenter.state.collectAsState()
                ConsentDialog(
                    state = state,
                    onApprove = {
                        (presenter.state.value as? PanePresenter.State.Consent)?.let { consent ->
                            paneScope.launch {
                                val granted = issueCertification(consent.nonce)
                                host.send<CertificationResponse>(granted)
                                presenter.onIssued(granted.securityLevel)
                            }
                        }
                    },
                    onDecline = {
                        paneScope.launch {
                            host.send<CertificationResponse>(CertificationResponse.Declined("user declined"))
                            presenter.onDeclined("user declined")
                        }
                    },
                    onStartOver = { lastRequest?.let(presenter::onRequest) },
                    onDone = { host.close() },
                    onDismiss = { host.close() },
                )
            }
        }

        val content = object : PipeContent {
            override val view: View = composeView
            override fun onMessage(message: PipeMessage) {
                PipeCodec.decodeOrNull<CertificationRequest>(message)?.let { req ->
                    lastRequest = req
                    presenter.onRequest(req)
                }
            }
        }
        // Full-screen transparent pane, edge-to-edge so the scrim dims the whole screen (status
        // bar included); the dialog card applies safe-drawing insets itself. The dialog animates
        // itself in (see ConsentDialog).
        return PaneResult.Content(
            content,
            PaneSpec(edgeToEdge = true),
        )
    }
}
