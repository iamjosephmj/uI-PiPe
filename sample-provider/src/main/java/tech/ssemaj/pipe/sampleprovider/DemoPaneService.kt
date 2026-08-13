package tech.ssemaj.pipe.sampleprovider

import android.view.ContextThemeWrapper
import android.view.View
import kotlinx.coroutines.launch
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.provider.HostHandle
import tech.ssemaj.pipe.provider.PaneResult
import tech.ssemaj.pipe.provider.PipeContent
import tech.ssemaj.pipe.provider.PipeProviderService
import tech.ssemaj.pipe.samples.contract.CertificationRequest
import tech.ssemaj.pipe.samples.contract.CertificationResponse
import tech.ssemaj.pipe.sampleprovider.data.KeystoreRepository
import tech.ssemaj.pipe.sampleprovider.domain.IssueCertificationUseCase
import tech.ssemaj.pipe.sampleprovider.pane.ConsentPaneView
import tech.ssemaj.pipe.sampleprovider.pane.PanePresenter
import tech.ssemaj.pipe.serialization.PipeCodec
import tech.ssemaj.pipe.serialization.send

/**
 * Sample provider: renders a consent pane and, on approval, signs the host's challenge with a
 * hardware-backed AndroidKeyStore key. It wires three collaborators together and holds no view or
 * crypto logic itself:
 *  - [ConsentPaneView] — the view (display + clicks),
 *  - [PanePresenter] — the pane's UI state machine,
 *  - [IssueCertificationUseCase] — the attestation signing.
 */
class DemoPaneService : PipeProviderService() {

    private val issueCertification = IssueCertificationUseCase(KeystoreRepository())

    override suspend fun onOpenPane(request: PipeRequest, host: HostHandle): PaneResult {
        val presenter = PanePresenter()
        val pane = ConsentPaneView(ContextThemeWrapper(this, R.style.Theme_PipeProvider))
        var lastRequest: CertificationRequest? = null

        pane.onApprove = {
            (presenter.state.value as? PanePresenter.State.Consent)?.let { consent ->
                paneScope.launch {
                    val granted = issueCertification(consent.nonce)
                    host.send<CertificationResponse>(granted)
                    presenter.onIssued(granted.securityLevel)
                }
            }
        }
        pane.onDecline = {
            paneScope.launch {
                host.send<CertificationResponse>(CertificationResponse.Declined("user declined"))
                presenter.onDeclined("user declined")
            }
        }
        pane.onStartOver = { lastRequest?.let(presenter::onRequest) }
        pane.onDone = { host.close() }

        // Render presenter state (paneScope is main-thread).
        paneScope.launch { presenter.state.collect(pane::render) }

        return PaneResult.Content(object : PipeContent {
            override val view: View = pane.view
            override fun onMessage(message: PipeMessage) {
                PipeCodec.decodeOrNull<CertificationRequest>(message)?.let { req ->
                    lastRequest = req
                    presenter.onRequest(req)
                }
            }
        })
    }
}
