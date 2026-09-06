package tech.ssemaj.pipe.samplesolo

import android.app.Application
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.core.PipeState
import tech.ssemaj.pipe.host.PipeBindImportance
import tech.ssemaj.pipe.host.PipeFullScreen
import tech.ssemaj.pipe.host.PipeSession
import tech.ssemaj.pipe.host.ProviderComponent

/** Band hues — shared with the panes so host chips and pane bands visually pair up. */
val BandHues = mapOf(
    'A' to Color(0xFF3B82F6),
    'B' to Color(0xFF8B5CF6),
    'C' to Color(0xFF10B981),
)

/**
 * Host side of the "same app, N processes" demo. This Activity runs in the app's **main** process
 * and opens panes against [PaneService] / [PaneServiceA-C] — services the manifest pins to
 * separate `:pane*` processes of this same app.
 *
 * Nothing about the library changes for this: the provider is just this app's own component, and
 * the default `sameSigningKey` gate is trivially satisfied. The panes render from other processes
 * with their own heaps and UI threads — seamlessly, in this Activity's window — and report their
 * taps back over the message channel, so the counters below are cross-process, live.
 */
class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var singleSession by mutableStateOf<PipeSession?>(null)
    private var singleStatus by mutableStateOf("not open")
    private val bandSessions = mutableStateMapOf<Char, PipeSession>()
    private val bandTaps = mutableStateMapOf('A' to 0, 'B' to 0, 'C' to 0)
    private var error by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SoloTheme {
                SoloScreen(
                    hostProc = Application.getProcessName(),
                    hostPid = Process.myPid(),
                    hostTid = Process.myTid(),
                    hostUid = Process.myUid(),
                    singleOpen = singleSession != null,
                    singleStatus = singleStatus,
                    bandsOpen = bandSessions.isNotEmpty(),
                    bandTaps = bandTaps,
                    error = error,
                    onOpenSingle = ::openSingle,
                    onOpenBands = ::openBands,
                    onCloseAll = ::closeAll,
                )
            }
        }
    }

    private fun openSingle() {
        error = null
        singleStatus = "opening…"
        val extras = Bundle().apply {
            putInt("hostPid", Process.myPid())
            putString("hostProc", Application.getProcessName())
            putInt("hostTid", Process.myTid())
        }
        PipeFullScreen.open(
            activity = this,
            provider = ProviderComponent(packageName, "$packageName.PaneService"),
            request = PipeRequest("solo.demo", extras),
            // Full-bleed scrim pane: dim the host's own system-bar strips to match.
            dimSystemBars = true,
            onSession = { session ->
                singleSession = session
                singleStatus = "open — one window, two processes"
                scope.launch {
                    session.state.first { it is PipeState.Closed }
                    singleSession = null
                    singleStatus = "closed"
                }
            },
            onError = { e ->
                singleStatus = "failed"
                error = e::class.simpleName
            },
        )
    }

    /** Host + :paneA + :paneB + :paneC = four processes, three independently interactive bands. */
    private fun openBands() {
        error = null
        BandHues.keys.forEach { letter ->
            PipeFullScreen.open(
                activity = this,
                provider = ProviderComponent(packageName, "$packageName.PaneService$letter"),
                request = PipeRequest("multi"),
                // Same app, fully trusted panes: let each pane process inherit the host's top
                // priority so all four processes sit at oom_score_adj 0 while the panes are up.
                bindImportance = PipeBindImportance.IMPORTANT,
                onSession = { session ->
                    bandSessions[letter] = session
                    scope.launch {
                        session.messages.collect { m -> onBandMessage(m) }
                    }
                    scope.launch {
                        session.state.first { it is PipeState.Closed }
                        bandSessions.remove(letter)
                    }
                },
                onError = { e -> error = "PaneService$letter: ${e::class.simpleName}" },
            )
        }
    }

    /** A band tapped itself in its own process and told us — live, across the binder. */
    private fun onBandMessage(m: PipeMessage) {
        val band = m.payload.getString("band")?.firstOrNull() ?: return
        bandTaps[band] = m.payload.getInt("taps")
    }

    private fun closeAll() {
        singleSession?.close()
        bandSessions.values.forEach { it.close() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoloScreen(
    hostProc: String,
    hostPid: Int,
    hostTid: Int,
    hostUid: Int,
    singleOpen: Boolean,
    singleStatus: String,
    bandsOpen: Boolean,
    bandTaps: Map<Char, Int>,
    error: String?,
    onOpenSingle: () -> Unit,
    onOpenBands: () -> Unit,
    onCloseAll: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("uI-PiPe Solo") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            SoloOrb(active = singleOpen || bandsOpen)
            Spacer(Modifier.height(20.dp))
            Text(
                "One app. Four processes.",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Panes rendered by this app's own :pane processes — same window, separate heaps, separate UI threads.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            FactsCard(hostProc, hostPid, hostTid, hostUid)
            Spacer(Modifier.height(24.dp))

            DemoCard(
                icon = { Icon(Icons.Rounded.Layers, null, tint = MaterialTheme.colorScheme.primary) },
                title = "Isolation demo",
                subtitle = "One full-screen pane from this app's :pane process — it shows the host/pane pid·tid proof.",
            ) {
                DemoButton(
                    label = if (singleOpen) "Pane open" else "Open single pane",
                    onClick = onOpenSingle,
                    enabled = !singleOpen,
                    filled = true,
                )
                StatusLine(singleStatus)
            }
            Spacer(Modifier.height(16.dp))

            DemoCard(
                icon = { Icon(Icons.Rounded.GridView, null, tint = MaterialTheme.colorScheme.tertiary) },
                title = "Tiling demo",
                subtitle = "Three banded panes, each from its own process — every band is independently interactive, and reports its taps back here.",
            ) {
                DemoButton(
                    label = if (bandsOpen) "Bands open" else "Open 3 tiled panes",
                    onClick = onOpenBands,
                    enabled = !bandsOpen,
                    filled = false,
                )
                AnimatedContent(
                    targetState = bandsOpen,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "bandsState",
                ) { open ->
                    if (open) {
                        Column(Modifier.padding(top = 12.dp).animateContentSize(tween(240))) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BandHues.forEach { (letter, hue) -> TapChip(letter, hue, bandTaps[letter] ?: 0) }
                            }
                            Spacer(Modifier.height(12.dp))
                            DemoButton(label = "Close all panes", onClick = onCloseAll, enabled = true, filled = true)
                        }
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(it, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SoloOrb(active: Boolean) {
    val tint = MaterialTheme.colorScheme.primary
    val pulse = rememberInfiniteTransition(label = "orb")
    val glowScale by pulse.animateFloat(
        0.85f, 1.25f,
        infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "glowScale",
    )
    val sweep by pulse.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(1400)),
        label = "sweep",
    )
    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        if (active) {
            Box(
                Modifier
                    .size(132.dp)
                    .graphicsLayer { scaleX = glowScale; scaleY = glowScale; alpha = 0.25f }
                    .background(tint.copy(alpha = 0.5f), CircleShape),
            )
        }
        Box(
            Modifier
                .size(108.dp)
                .background(tint.copy(alpha = 0.13f), CircleShape)
                .drawBehind {
                    drawArc(
                        color = tint,
                        startAngle = if (active) sweep else -90f,
                        sweepAngle = if (active) 80f else 360f,
                        useCenter = false,
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "1×4",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
        }
    }
}

@Composable
private fun FactsCard(proc: String, pid: Int, tid: Int, uid: Int) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("HOST PROCESS", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            Fact("name", proc)
            Fact("pid", pid.toString())
            Fact("uid", uid.toString())
            Fact("ui thread", "main · tid $tid")
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun DemoCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                icon()
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun DemoButton(label: String, onClick: () -> Unit, enabled: Boolean, filled: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "demoScale",
    )
    val modifier = Modifier
        .fillMaxWidth()
        .height(54.dp)
        .graphicsLayer { scaleX = scale; scaleY = scale }
    if (filled) {
        Button(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interaction,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = modifier,
        ) { Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium) }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interaction,
            shape = RoundedCornerShape(24.dp),
            modifier = modifier,
        ) { Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun StatusLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A live cross-process counter — the band tapped itself in another process and told us. */
@Composable
private fun TapChip(letter: Char, hue: Color, count: Int) {
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(count) {
        if (count > 0) {
            scale.snapTo(1.3f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .background(hue.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(Modifier.size(10.dp).background(hue, CircleShape))
        Text(
            "$letter × $count",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
