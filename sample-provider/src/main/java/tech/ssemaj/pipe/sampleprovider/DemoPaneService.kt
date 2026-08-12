package tech.ssemaj.pipe.sampleprovider

import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
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
import tech.ssemaj.pipe.sampleprovider.pane.PanePresenter
import tech.ssemaj.pipe.serialization.PipeCodec
import tech.ssemaj.pipe.serialization.send

class DemoPaneService : PipeProviderService() {

    private val issueCertification = IssueCertificationUseCase(KeystoreRepository())

    override suspend fun onOpenPane(request: PipeRequest, host: HostHandle): PaneResult {
        val themed = ContextThemeWrapper(this, R.style.Theme_PipeProvider)
        val presenter = PanePresenter()
        var lastRequest: CertificationRequest? = null
        val pad = if (request.presentation == tech.ssemaj.pipe.core.PipePresentation.DIALOG) 24 else 48

        fun text(value: String, sizeSp: Float = 14f, bold: Boolean = false) = TextView(themed).apply {
            this.text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(Color.parseColor("#1C1B1F"))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

        val title = text("Pipe Certification Provider", 16f, bold = true)
        val caption = text("pane-ready")
        val consentTitle = text("", 15f, bold = true)
        val consentFingerprint = text("", 12f)
        val approve = MaterialButton(themed).apply { text = "Approve" }
        val decline = MaterialButton(
            themed, null, com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply { text = "Decline" }
        val buttons = LinearLayout(themed).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(decline, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(approve, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = 16 })
        }
        val consentGroup = LinearLayout(themed).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(consentTitle); addView(consentFingerprint); addView(buttons)
        }
        val resultText = text("", 15f, bold = true).apply { visibility = View.GONE }
        val startOver = MaterialButton(themed).apply {
            text = "Start over"
            visibility = View.GONE
            setOnClickListener { lastRequest?.let(presenter::onRequest) }
        }
        val root = LinearLayout(themed).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.WHITE)
            setPadding(pad, pad, pad, pad)
            addView(title); addView(caption); addView(consentGroup); addView(resultText); addView(startOver)
        }

        // Render presenter state (paneScope is main-thread).
        paneScope.launch {
            presenter.state.collect { state ->
                consentGroup.visibility = if (state is PanePresenter.State.Consent) View.VISIBLE else View.GONE
                resultText.visibility =
                    if (state is PanePresenter.State.Issued || state is PanePresenter.State.Declined) View.VISIBLE
                    else View.GONE
                startOver.visibility =
                    if (state is PanePresenter.State.Issued || state is PanePresenter.State.Declined) View.VISIBLE
                    else View.GONE
                when (state) {
                    PanePresenter.State.Idle -> caption.text = "pane-ready"
                    is PanePresenter.State.Consent -> {
                        caption.text = "consent required"
                        consentTitle.text = "${state.hostName} requests a device certification"
                        consentFingerprint.text = "challenge ${state.nonceFingerprint}"
                    }
                    is PanePresenter.State.Issued -> resultText.text = "Certification issued"
                    is PanePresenter.State.Declined -> resultText.text = "Certification declined"
                }
            }
        }

        approve.setOnClickListener {
            val consent = presenter.state.value as? PanePresenter.State.Consent ?: return@setOnClickListener
            paneScope.launch {
                val granted = issueCertification(consent.nonce)
                host.send<CertificationResponse>(granted)
                presenter.onIssued(granted.securityLevel)
            }
        }
        decline.setOnClickListener {
            paneScope.launch {
                host.send<CertificationResponse>(CertificationResponse.Declined("user declined"))
                presenter.onDeclined("user declined")
            }
        }

        return PaneResult.Content(object : PipeContent {
            override val view: View = root
            override fun onMessage(message: PipeMessage) {
                PipeCodec.decodeOrNull<CertificationRequest>(message)?.let { req ->
                    lastRequest = req
                    presenter.onRequest(req)
                }
            }
        })
    }

    private companion object { const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT }
}
