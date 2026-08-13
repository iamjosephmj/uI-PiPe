package tech.ssemaj.pipe.sampleprovider.pane

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/**
 * The consent pane's view tree — a passive view-holder. It knows how to *display* a
 * [PanePresenter.State] via [render] and reports clicks through the `on…` callbacks; it holds no
 * business logic, so [DemoPaneService][tech.ssemaj.pipe.sampleprovider.DemoPaneService] can wire the
 * presenter and the certification use case around it.
 */
internal class ConsentPaneView(private val ctx: Context) {

    var onApprove: () -> Unit = {}
    var onDecline: () -> Unit = {}
    var onStartOver: () -> Unit = {}
    var onDone: () -> Unit = {}

    private fun text(value: String, sizeSp: Float = 14f, bold: Boolean = false) = TextView(ctx).apply {
        this.text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(Color.parseColor("#1C1B1F"))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun outlinedButton(label: String) = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle,
    ).apply { text = label }

    /** A horizontal pair of equal-weight buttons — the consent and finish rows share this shape. */
    private fun buttonRow(start: View, end: View) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(start, LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(end, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = 16 })
    }

    private val caption = text("pane-ready")
    private val consentTitle = text("", 15f, bold = true)
    private val consentFingerprint = text("", 12f)
    private val consentGroup = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        addView(consentTitle); addView(consentFingerprint)
        addView(buttonRow(outlinedButton("Decline").also { it.setOnClickListener { onDecline() } },
            MaterialButton(ctx).apply { text = "Approve"; setOnClickListener { onApprove() } }))
    }
    private val resultText = text("", 15f, bold = true).apply { visibility = View.GONE }
    private val finishButtons = buttonRow(
        outlinedButton("Start over").also { it.setOnClickListener { onStartOver() } },
        MaterialButton(ctx).apply { text = "Done"; setOnClickListener { onDone() } },
    ).apply { visibility = View.GONE }

    /** Root view to hand to [PipeContent][tech.ssemaj.pipe.provider.PipeContent]. */
    val view: View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundColor(Color.WHITE)
        setPadding(PAD, PAD, PAD, PAD)
        addView(text("Pipe Certification Provider", 16f, bold = true))
        addView(caption); addView(consentGroup); addView(resultText); addView(finishButtons)
    }

    /** Reflect [state] into the pane. Idempotent — safe to call on every emission. */
    fun render(state: PanePresenter.State) {
        val finished = state is PanePresenter.State.Issued || state is PanePresenter.State.Declined
        consentGroup.visibility = if (state is PanePresenter.State.Consent) View.VISIBLE else View.GONE
        resultText.visibility = if (finished) View.VISIBLE else View.GONE
        finishButtons.visibility = if (finished) View.VISIBLE else View.GONE
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

    private companion object {
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        const val PAD = 48
    }
}
