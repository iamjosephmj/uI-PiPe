package tech.ssemaj.pipe.samplesolo

import android.app.Application
import android.os.Process
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.provider.HostHandle
import tech.ssemaj.pipe.provider.PipeContent
import tech.ssemaj.pipe.provider.PaneResult
import tech.ssemaj.pipe.provider.PaneSpec
import tech.ssemaj.pipe.provider.PipeProviderService

private val DialogEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

private val PaneScheme = darkColorScheme(
    primary = Color(0xFF5BDAD2),
    onPrimary = Color(0xFF003735),
    background = Color(0xFF101315),
    onBackground = Color(0xFFE1E3E1),
    surface = Color(0xFF16191B),
    onSurface = Color(0xFFE1E3E1),
    surfaceVariant = Color(0xFF404746),
    onSurfaceVariant = Color(0xFFBFC9C6),
    outline = Color(0xFF89938F),
)

/**
 * The pane, running in this app's `:pane` process (see the manifest). It draws the host's and its
 * own process facts side by side so you can *see* they differ — same app, same UID, two processes,
 * one window.
 */
class PaneService : PipeProviderService() {

    override suspend fun onOpenPane(request: PipeRequest, host: HostHandle): PaneResult {
        val hostPid = request.extras.getInt("hostPid", -1)
        val hostProc = request.extras.getString("hostProc") ?: "?"
        val hostTid = request.extras.getInt("hostTid", -1)

        val view = ComposeView(this).apply {
            setContent {
                MaterialTheme(colorScheme = PaneScheme) {
                    IsolationDialog(
                        hostProc = hostProc,
                        hostPid = hostPid,
                        hostTid = hostTid,
                        paneProc = Application.getProcessName(),
                        panePid = Process.myPid(),
                        paneTid = Process.myTid(),
                        uid = Process.myUid(),
                        onClose = { host.close() },
                    )
                }
            }
        }
        // Edge-to-edge: the scrim dims the whole screen, status bar included; the centered card
        // applies safe-drawing insets itself so it never underlaps the bars.
        return PaneResult.Content(
            object : PipeContent { override val view: View = view },
            PaneSpec(edgeToEdge = true),
        )
    }
}

@Composable
private fun IsolationDialog(
    hostProc: String,
    hostPid: Int,
    hostTid: Int,
    paneProc: String,
    panePid: Int,
    paneTid: Int,
    uid: Int,
    onClose: () -> Unit,
) {
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xB3000000)),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visibleState = shown,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.90f, animationSpec = tween(380, easing = DialogEasing)),
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Pane rendered from a separate process",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    ProcessRow("HOST", hostProc, hostPid, hostTid, Color(0xFF6FB7FF))
                    Spacer(Modifier.height(10.dp))
                    ProcessRow("PANE", paneProc, panePid, paneTid, Color(0xFFB79CFF))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Same app · same UID ($uid) · two processes · one window",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(22.dp))
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        if (pressed) 0.97f else 1f,
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        label = "closeScale",
                    )
                    Button(
                        onClick = onClose,
                        interactionSource = interaction,
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale },
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.height(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Close", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessRow(tag: String, proc: String, pid: Int, tid: Int, tint: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .background(tint.copy(alpha = 0.25f), CircleShape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(tag, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = tint)
        }
        Column(Modifier.weight(1f)) {
            Text(proc, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            Text(
                "pid $pid · ui tid $tid",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
