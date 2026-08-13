package tech.ssemaj.pipe.sampleprovider.pane

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The whole consent experience, drawn *inside* the provider's full-screen transparent pane, as a
 * dark, bottom-anchored sheet that slides up (the stock bottom-sheet motion — a plain slide, no
 * fade). The pane library knows nothing about this — it's an ordinary Compose sheet the provider
 * composes itself. (Material's own `ModalBottomSheet` can't be used here: it creates its own window
 * and needs an Activity token, which a Service-owned pane doesn't have.)
 */
@Composable
fun ConsentSheet(
    state: PanePresenter.State,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onStartOver: () -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val shown = remember { MutableTransitionState(false).apply { targetState = true } }
        // Scrim fills the pane (= the whole host window); the sheet slides up from the bottom.
        Box(
            Modifier.fillMaxSize().background(Color(0xB3000000)).noRippleClick(onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(visibleState = shown, enter = slideInVertically(initialOffsetY = { it })) {
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().noRippleClick {},
                ) {
                    Column(
                        Modifier.padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
                                .width(36.dp).height(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(2.dp),
                                ),
                        )
                        Text("Pipe Certification Provider", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface)
                        when (state) {
                            PanePresenter.State.Idle -> Caption("pane-ready")
                            is PanePresenter.State.Consent -> {
                                Caption("consent required")
                                Text("${state.hostName} requests a device certification",
                                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Caption("challenge ${state.nonceFingerprint}")
                                ButtonRow(
                                    secondaryLabel = "Decline", onSecondary = onDecline,
                                    primaryLabel = "Approve", onPrimary = onApprove,
                                )
                            }
                            is PanePresenter.State.Issued -> Result("Certification issued", onStartOver, onDone)
                            is PanePresenter.State.Declined -> Result("Certification declined", onStartOver, onDone)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Caption(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable private fun Result(text: String, onStartOver: () -> Unit, onDone: () -> Unit) {
    Text(text, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    ButtonRow("Start over", onStartOver, "Done", onDone)
}

@Composable private fun ButtonRow(
    secondaryLabel: String, onSecondary: () -> Unit,
    primaryLabel: String, onPrimary: () -> Unit,
) {
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onSecondary, modifier = Modifier.weight(1f)) { Text(secondaryLabel) }
        Button(onClick = onPrimary, modifier = Modifier.weight(1f)) { Text(primaryLabel) }
    }
}

/** Clickable with no ripple/indication — for the scrim and to consume sheet taps. */
private fun Modifier.noRippleClick(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(interactionSource = MutableInteractionSourceHolder, indication = null, onClick = onClick),
)

private val MutableInteractionSourceHolder = MutableInteractionSource()
