package tech.ssemaj.pipe.samplehost.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tech.ssemaj.pipe.samplehost.domain.CertSummary
import tech.ssemaj.pipe.samplehost.domain.CertificationResult
import tech.ssemaj.pipe.samplehost.presentation.FlowPhase
import tech.ssemaj.pipe.samplehost.presentation.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificationScreen(
    state: UiState,
    onStart: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Pipe Certification") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            StatusOrb(state.phase)
            Spacer(Modifier.height(20.dp))
            PhaseHeadline(state.phase)
            Spacer(Modifier.height(28.dp))
            StepRail(state.phase)
            Spacer(Modifier.height(32.dp))
            CertifyButton(state, onStart)
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState = state.phase,
                transitionSpec = {
                    (fadeIn(tween(260)) + slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 6 })
                        .togetherWith(fadeOut(tween(160)))
                },
                label = "phaseCard",
            ) { phase ->
                PhaseCard(phase)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Hero: a status-driven orb — the single element that tells the whole story.
// ---------------------------------------------------------------------------

@Composable
private fun StatusOrb(phase: FlowPhase, modifier: Modifier = Modifier) {
    val success = phase is FlowPhase.Done && phase.result.verified
    val (icon, tint) = when (phase) {
        FlowPhase.Idle -> Icons.Rounded.Key to MaterialTheme.colorScheme.primary
        FlowPhase.WaitingForProvider -> Icons.AutoMirrored.Rounded.Send to MaterialTheme.colorScheme.tertiary
        FlowPhase.Verifying -> Icons.Rounded.Fingerprint to MaterialTheme.colorScheme.primary
        is FlowPhase.Done ->
            if (success) Icons.Rounded.WorkspacePremium to MaterialTheme.colorScheme.primary
            else Icons.Rounded.GppBad to MaterialTheme.colorScheme.error
        is FlowPhase.Declined -> Icons.Rounded.ThumbDown to MaterialTheme.colorScheme.tertiary
        FlowPhase.Timeout -> Icons.Rounded.HourglassTop to MaterialTheme.colorScheme.secondary
        is FlowPhase.PipeFailure -> Icons.Rounded.GppBad to MaterialTheme.colorScheme.error
    }

    val active = phase is FlowPhase.WaitingForProvider || phase is FlowPhase.Verifying
    val container by animateColorAsState(tint.copy(alpha = 0.14f), tween(400), label = "orbContainer")
    val ring by animateColorAsState(tint.copy(alpha = 0.35f), tween(400), label = "orbRing")

    // Breathing glow while the flow is live; still otherwise.
    val pulse = rememberInfiniteTransition(label = "orbPulse")
    val glowScale by pulse.animateFloat(
        0.85f, 1.25f,
        infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowScale",
    )
    val glowAlpha by pulse.animateFloat(
        0.30f, 0.0f,
        infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "glowAlpha",
    )
    // Rotating sweep while the host is verifying.
    val sweep by pulse.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "sweep",
    )

    Box(modifier.size(148.dp), contentAlignment = Alignment.Center) {
        if (active) {
            Box(
                Modifier
                    .size(148.dp)
                    .graphicsLayer {
                        scaleX = glowScale
                        scaleY = glowScale
                        alpha = if (active) glowAlpha else 0f
                    }
                    .background(tint.copy(alpha = 0.25f), CircleShape),
            )
        }
        if (phase is FlowPhase.Verifying) {
            Box(
                Modifier
                    .size(128.dp)
                    .drawBehind {
                        drawArc(
                            color = tint,
                            startAngle = sweep,
                            sweepAngle = 80f,
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                        )
                    },
            )
        }
        Box(
            Modifier
                .size(118.dp)
                .background(container, CircleShape)
                .drawBehind {
                    drawCircle(style = Stroke(width = 1.5.dp.toPx()), color = ring, radius = size.minDimension / 2f)
                },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = icon,
                transitionSpec = {
                    (scaleIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        initialScale = 0.5f,
                    ) + fadeIn(tween(150)))
                        .togetherWith(fadeOut(tween(120)))
                },
                label = "orbIcon",
            ) { i ->
                Icon(i, contentDescription = null, tint = tint, modifier = Modifier.size(48.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Headline: phase-driven, animated in place.
// ---------------------------------------------------------------------------

private data class Headline(val title: String, val subtitle: String)

@Composable
private fun PhaseHeadline(phase: FlowPhase) {
    val headline = when (phase) {
        FlowPhase.Idle -> Headline(
            "Verified cross-process certification",
            "One app's live screen, rendered inside another — cryptographically verified on both ends.",
        )
        FlowPhase.WaitingForProvider -> Headline(
            "Challenge sent",
            "The consent sheet is showing in the provider's own pane, over this screen. Approve it there.",
        )
        FlowPhase.Verifying -> Headline(
            "Verifying certification",
            "Checking the signature and the hardware attestation chain, right here in the host.",
        )
        is FlowPhase.Done -> Headline(
            "Certification complete",
            if (phase.result.verified) "The provider's signed attestation checked out." else "The attestation did not check out.",
        )
        is FlowPhase.Declined -> Headline("Provider declined", "The provider chose not to issue a certification.")
        FlowPhase.Timeout -> Headline("No response", "The provider did not answer in time.")
        is FlowPhase.PipeFailure -> Headline("Pipe error", "The pane flow failed before a session opened.")
    }
    AnimatedContent(
        targetState = headline,
        transitionSpec = {
            (slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(260)))
                .togetherWith(slideOutVertically(tween(200)) { -it / 4 } + fadeOut(tween(160)))
        },
        label = "headline",
    ) { (title, subtitle) ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Step rail: challenge → provider → verify, with an animated progress track.
// ---------------------------------------------------------------------------

private enum class StepState { Pending, Active, Done }

@Composable
private fun StepRail(phase: FlowPhase) {
    val states = when (phase) {
        FlowPhase.Idle -> listOf(StepState.Pending, StepState.Pending, StepState.Pending)
        FlowPhase.WaitingForProvider -> listOf(StepState.Done, StepState.Active, StepState.Pending)
        FlowPhase.Verifying -> listOf(StepState.Done, StepState.Done, StepState.Active)
        is FlowPhase.Done -> listOf(StepState.Done, StepState.Done, StepState.Done)
        else -> listOf(StepState.Done, StepState.Done, StepState.Done)
    }
    val fillTarget = when (phase) {
        FlowPhase.Idle -> 0f
        FlowPhase.WaitingForProvider -> 0.5f
        else -> 1f
    }
    val fill by animateFloatAsState(fillTarget, tween(500, easing = FastOutSlowInEasing), label = "railFill")

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .height(22.dp),
        ) {
            // Base track.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
            )
            // Animated fill.
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(fraction = fill)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
            )
            listOf(Alignment.CenterStart, Alignment.Center, Alignment.CenterEnd).forEachIndexed { i, alignment ->
                StepDot(states[i], Modifier.align(alignment))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("Challenge", "Provider", "Verify").forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StepDot(state: StepState, modifier: Modifier = Modifier) {
    val container by animateColorAsState(
        when (state) {
            StepState.Pending -> MaterialTheme.colorScheme.surfaceVariant
            StepState.Active, StepState.Done -> MaterialTheme.colorScheme.primary
        },
        tween(300),
        label = "dotContainer",
    )
    val contentColor = if (state == StepState.Pending) MaterialTheme.colorScheme.outline
    else MaterialTheme.colorScheme.onPrimary
    val pendingOutline = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    Box(modifier.size(22.dp), contentAlignment = Alignment.Center) {
        if (state == StepState.Active) {
            val pulse = rememberInfiniteTransition(label = "dotPulse")
            val ringAlpha by pulse.animateFloat(
                0.45f, 0f,
                infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing)),
                label = "ringAlpha",
            )
            val ringScale by pulse.animateFloat(
                1f, 1.8f,
                infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing)),
                label = "ringScale",
            )
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { scaleX = ringScale; scaleY = ringScale; alpha = ringAlpha }
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        Box(
            Modifier
                .size(22.dp)
                .background(container, CircleShape)
                .drawBehind {
                    if (state == StepState.Pending) {
                        drawCircle(
                            style = Stroke(1.5.dp.toPx()),
                            color = pendingOutline,
                            radius = size.minDimension / 2f - 0.75.dp.toPx(),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val showCheck by animateFloatAsState(
                if (state == StepState.Done) 1f else 0f,
                tween(200, delayMillis = if (state == StepState.Done) 150 else 0),
                label = "dotCheck",
            )
            if (showCheck > 0.5f) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = contentColor, modifier = Modifier.size(13.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// CTA: press-scale, animated label swap, springy restore.
// ---------------------------------------------------------------------------

@Composable
private fun CertifyButton(state: UiState, onStart: () -> Unit) {
    val terminal = state.phase is FlowPhase.Done || state.phase is FlowPhase.Declined ||
        state.phase is FlowPhase.Timeout || state.phase is FlowPhase.PipeFailure
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "ctaScale",
    )

    Button(
        onClick = onStart,
        enabled = !state.inProgress,
        interactionSource = interaction,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        AnimatedContent(
            targetState = terminal,
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(initialScale = 0.7f, animationSpec = spring(stiffness = Spring.StiffnessMedium)))
                    .togetherWith(fadeOut(tween(120)))
            },
            label = "ctaLabel",
        ) { isTerminal ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (isTerminal) Icons.Rounded.Verified else Icons.AutoMirrored.Rounded.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    if (isTerminal) "Certify again" else "Request certification",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Phase cards: everything below the CTA.
// ---------------------------------------------------------------------------

@Composable
private fun PhaseCard(phase: FlowPhase) {
    when (phase) {
        FlowPhase.Idle -> {}
        FlowPhase.WaitingForProvider -> ProgressCard("Challenge sent — waiting for provider approval")
        FlowPhase.Verifying -> ProgressCard("Verifying certification")
        is FlowPhase.Done -> ResultCard(phase.result)
        is FlowPhase.Declined -> MessageCard("Provider declined: ${phase.reason}", style = MessageStyle.Declined)
        FlowPhase.Timeout -> MessageCard("No response from provider", style = MessageStyle.Neutral)
        is FlowPhase.PipeFailure -> MessageCard(phase.text, style = MessageStyle.Error)
    }
}

private enum class MessageStyle { Declined, Neutral, Error }

@Composable
private fun ProgressCard(text: String) {
    val pulse = rememberInfiniteTransition(label = "progressPulse")
    val alpha by pulse.animateFloat(
        0.35f, 1f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "progressAlpha",
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MessageCard(text: String, style: MessageStyle) {
    val (container, content, icon) = when (style) {
        MessageStyle.Declined -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Rounded.ThumbDown,
        )
        MessageStyle.Neutral -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Rounded.HourglassTop,
        )
        MessageStyle.Error -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Rounded.GppBad,
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = content)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ---------------------------------------------------------------------------
// Result card: springy verdict, staggered verification chips, growing chain.
// ---------------------------------------------------------------------------

@Composable
private fun ResultCard(result: CertificationResult) {
    var expanded by remember { mutableStateOf(false) }
    val verified = result.verified

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (verified) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PopInIcon(
                    icon = if (verified) Icons.Rounded.Check else Icons.Rounded.Close,
                    tint = if (verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Column {
                    Text(
                        when {
                            !verified -> "Verification failed"
                            result.hardwareBacked -> "Hardware-verified"
                            else -> "Software-backed"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    if (verified) {
                        Text(
                            if (result.hardwareBacked) "Attestation chain rooted in device hardware"
                            else "No hardware attestation on this device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Check(label = "Signature", ok = result.signatureOk, delayMillis = 100)
                Check(
                    label = "Challenge",
                    ok = result.challengeOk,
                    delayMillis = 220,
                )
                Check(label = "Chain", ok = result.chainOk, delayMillis = 340)
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(tween(280, easing = FastOutSlowInEasing)),
            ) {
                TextButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (expanded) "Hide certificate chain" else "Show certificate chain")
                }
                AnimatedVisibility(visible = expanded, enter = fadeIn(tween(240)) + slideInVertically { it / 8 }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        result.certificates.forEachIndexed { index, cert ->
                            CertRow(cert, delayMillis = 80L * index)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PopInIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    val scale by animateFloatAsState(
        if (state.targetState) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "popIn",
    )
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(tint.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun Check(label: String, ok: Boolean?, delayMillis: Int) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        shown.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }
    val failed = ok == false
    val tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .graphicsLayer {
                alpha = shown.value
                translationY = (1f - shown.value) * 8.dp.toPx()
            }
            .background(tint.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(
            if (failed) Icons.Rounded.Close else Icons.Rounded.Check,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(
            "$label ${if (ok == null) "n/a" else if (ok) "ok" else "FAILED"}",
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CertRow(cert: CertSummary, delayMillis: Long) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        shown.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = shown.value
                translationX = (1f - shown.value) * 16.dp.toPx()
            }
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(cert.subject, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text("issuer: ${cert.issuer}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "sha256: ${cert.sha256.take(32)}…",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
