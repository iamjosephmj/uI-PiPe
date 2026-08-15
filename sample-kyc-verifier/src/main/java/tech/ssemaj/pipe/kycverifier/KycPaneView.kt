package tech.ssemaj.pipe.kycverifier

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ViewFlipper
import tech.ssemaj.pipe.core.CloseReason
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.provider.PipeContent
import tech.ssemaj.pipe.samples.kyc.KycStatus
import tech.ssemaj.pipe.serialization.PipeCodec
import tech.ssemaj.pipe.samples.kyc.KycRequest

/**
 * The verifier's UI, rendered inside the host's window as a uI-PiPe pane. A small mock wizard —
 * no real camera or PII. [onDecision] is invoked once with the user's outcome; [onClose] dismisses.
 */
class KycPaneView(
    ctx: Context,
    private val bankName: String,
    private val onDecision: (KycStatus) -> Unit,
    private val onClose: () -> Unit,
) : PipeContent {

    private val flipper = ViewFlipper(ctx)
    private lateinit var levelLabel: TextView

    override val view: View get() = flipper

    init {
        flipper.setBackgroundColor(Color.parseColor("#0D141D"))
        flipper.addView(screen(ctx, "Verify your identity",
            "VerifyID needs to confirm your identity for $bankName.", "Continue") { flipper.showNext() }.also { consentScreen ->
            levelLabel = TextView(ctx).apply {
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#8B98A5"))
            }
            (consentScreen as LinearLayout).addView(levelLabel)
        })
        flipper.addView(screen(ctx, "Scan your ID",
            "[ mock document frame ]\nNo real camera — this is a demo.", "Capture") { flipper.showNext() })
        flipper.addView(screen(ctx, "Liveness selfie",
            "[ mock selfie frame ]\nNo real camera — this is a demo.", "Capture") { flipper.showNext() })
        flipper.addView(screen(ctx, "Verified ✓",
            "Identity confirmed for $bankName.", "Done") { onDecision(KycStatus.APPROVED) })
    }

    private fun screen(ctx: Context, title: String, body: String, cta: String, onCta: () -> Unit): View =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(72, 72, 72, 72)
            addView(TextView(ctx).apply { text = title; textSize = 24f; setTextColor(Color.WHITE) })
            addView(TextView(ctx).apply {
                text = "\n$body\n"; textSize = 15f; gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#8B98A5"))
            })
            addView(Button(ctx).apply { text = cta; setOnClickListener { onCta() } })
            addView(Button(ctx).apply {
                text = "Cancel"; setOnClickListener { onDecision(KycStatus.DECLINED) }
            })
        }

    override fun onMessage(message: PipeMessage) {
        PipeCodec.decodeOrNull<KycRequest>(message)?.let { req ->
            levelLabel.text = "Requested level: ${req.level}"
        }
    }
    override fun onClosed(reason: CloseReason) { onClose() }
}
