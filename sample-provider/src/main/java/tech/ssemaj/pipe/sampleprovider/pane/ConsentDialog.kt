package tech.ssemaj.pipe.sampleprovider.pane

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tech.ssemaj.pipe.samples.contract.SecurityLevel

/** Emphasized decelerate — the M3 dialog standard. */
private val DialogEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

private val DialogScheme = darkColorScheme(
    primary = Color(0xFF5BDAD2),
    onPrimary = Color(0xFF003735),
    primaryContainer = Color(0xFF00504C),
    onPrimaryContainer = Color(0xFF7FF6ED),
    secondary = Color(0xFFB1CBC8),
    onSecondary = Color(0xFF1C3533),
    secondaryContainer = Color(0xFF334B49),
    onSecondaryContainer = Color(0xFFCDE7E3),
    tertiary = Color(0xFFA9C5FF),
    onTertiary = Color(0xFF1B304D),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF68010A),
    errorContainer = Color(0xFF8E1B17),
    onErrorContainer = Color(0xFFFFDAD3),
    background = Color(0xFF101315),
    onBackground = Color(0xFFE1E3E1),
    surface = Color(0xFF16191B),
    onSurface = Color(0xFFE1E3E1),
    surfaceVariant = Color(0xFF404746),
    onSurfaceVariant = Color(0xFFBFC9C6),
    outline = Color(0xFF89938F),
)

/** The emblem's teal gradient — the dialog's single accent moment. */
private val EmblemGradient = listOf(Color(0xFF6FE3DB), Color(0xFF15948C))

/**
 * The consent experience, drawn *inside* the provider's full-screen transparent pane as a dark,
 * centered dialog over a scrim. The design is a single focused column — emblem moment, the
 * request as the hero, a receipt-style challenge line, a two-item checklist of what is actually
 * agreed to, and one strong action. (Material's own `AlertDialog`/`ModalBottomSheet` can't be
 * used here: they create their own window and need an Activity token, which a Service-owned pane
 * doesn't have; the dialog motion is composed by hand.)
 */
@Composable
fun ConsentDialog(
    state: PanePresenter.State,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onStartOver: () -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    MaterialTheme(colorScheme = DialogScheme) {
        val shown = remember { MutableTransitionState(false).apply { targetState = true } }
        val scrim by animateFloatAsState(
            if (shown.targetState) 1f else 0f,
            tween(300, easing = FastOutSlowInEasing),
            label = "scrim",
        )

        Box(Modifier.fillMaxSize()) {
            // Scrim fills the pane (= the whole host window) and dismisses on tap.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrim }
                    .background(Color(0xB3000000))
                    .noRippleClick(onDismiss),
            )
            // The consent card floats center-screen and scales in — a dialog, not a sheet.
            AnimatedVisibility(
                visibleState = shown,
                enter = fadeIn(tween(180)) +
                    scaleIn(initialScale = 0.90f, animationSpec = tween(380, easing = DialogEasing)),
                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.95f, animationSpec = tween(150)),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .widthIn(max = 440.dp)
                        .noRippleClick {},
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AnimatedContent(
                            targetState = state,
                            contentKey = { it::class },
                            transitionSpec = {
                                (fadeIn(tween(240)) + scaleIn(initialScale = 0.97f, animationSpec = tween(240)))
                                    .togetherWith(fadeOut(tween(140)))
                            },
                            label = "dialogBody",
                        ) { s ->
                            when (s) {
                                PanePresenter.State.Idle -> IdleBody()
                                is PanePresenter.State.Consent -> ConsentBody(
                                    state = s,
                                    onApprove = onApprove,
                                    onDecline = onDecline,
                                )
                                is PanePresenter.State.Issued -> TerminalBody(
                                    mark = Mark.Check,
                                    markTint = MaterialTheme.colorScheme.primary,
                                    title = "Certification issued",
                                    details = { LevelChip(s.level) },
                                    onPrimary = onDone,
                                    onSecondary = onStartOver,
                                )
                                is PanePresenter.State.Declined -> TerminalBody(
                                    mark = Mark.Cross,
                                    markTint = MaterialTheme.colorScheme.error,
                                    title = "Certification declined",
                                    details = { Caption(s.reason) },
                                    onPrimary = onDone,
                                    onSecondary = onStartOver,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bodies
// ---------------------------------------------------------------------------

@Composable
private fun IdleBody() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Caption("pane-ready")
    }
}

@Composable
private fun ConsentBody(
    state: PanePresenter.State.Consent,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Emblem(Modifier.staggerIn(60), icon = Icons.Rounded.Key)
        Spacer(Modifier.height(18.dp))
        Overline("Pipe Certification Provider", Modifier.staggerIn(140))
        Spacer(Modifier.height(10.dp))
        Text(
            state.hostName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.staggerIn(200),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "requests a device certification",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.staggerIn(240),
        )
        Spacer(Modifier.height(22.dp))
        ChallengeRow(state.nonceFingerprint, Modifier.staggerIn(320))
        Spacer(Modifier.height(18.dp))
        ConsentPoint("Signs a one-time challenge", Modifier.staggerIn(400))
        Spacer(Modifier.height(8.dp))
        ConsentPoint("Key is created here, deleted after", Modifier.staggerIn(460))
        Spacer(Modifier.height(26.dp))
        PrimaryAction(
            label = "Approve",
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onApprove()
            },
            modifier = Modifier.staggerIn(540),
        )
        QuietAction(
            label = "Decline",
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDecline()
            },
            modifier = Modifier.staggerIn(600),
        )
    }
}

@Composable
private fun TerminalBody(
    mark: Mark,
    markTint: Color,
    title: String,
    details: @Composable () -> Unit,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Emblem(Modifier.staggerIn(60), content = { AnimatedMark(color = markTint, mark = mark) })
        Spacer(Modifier.height(18.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.staggerIn(160),
        )
        Spacer(Modifier.height(6.dp))
        Box(Modifier.staggerIn(240)) { details() }
        Spacer(Modifier.height(26.dp))
        PrimaryAction(label = "Done", onClick = onPrimary, modifier = Modifier.staggerIn(320))
        QuietAction(label = "Start over", onClick = onSecondary, leading = Icons.Rounded.Refresh,
            modifier = Modifier.staggerIn(380))
    }
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

/**
 * The dialog's anchor: a gradient disc with a hairline halo ring. [icon] supplies the consent
 * glyph; [content] is a slot for self-drawing marks (terminal states).
 */
@Composable
private fun Emblem(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    content: @Composable () -> Unit = {},
) {
    val entered = remember { MutableTransitionState(false).apply { targetState = true } }
    val scale by animateFloatAsState(
        if (entered.targetState) 1f else 0.4f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "emblemScale",
    )
    Box(modifier.graphicsLayer { scaleX = scale; scaleY = scale }, contentAlignment = Alignment.Center) {
        // Halo ring.
        Box(
            Modifier
                .size(104.dp)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f), CircleShape),
        )
        // Gradient disc.
        Box(
            Modifier
                .size(92.dp)
                .background(Brush.linearGradient(EmblemGradient), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color(0xFF06302D), modifier = Modifier.size(38.dp))
            } else {
                content()
            }
        }
    }
}

@Composable
private fun Overline(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 2.5.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/** Receipt-style line: what the challenge is, in monospace, in a hairline tray. */
@Composable
private fun ChallengeRow(fingerprint: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "challenge",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            fingerprint,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ConsentPoint(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Rounded.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LevelChip(level: SecurityLevel) {
    val (icon, label) = when (level) {
        SecurityLevel.HARDWARE -> Icons.Rounded.Memory to "Hardware attestation"
        SecurityLevel.SOFTWARE -> Icons.Rounded.Shield to "Software fallback"
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(14.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** Circle that draws itself, then a check/cross that traces along it. */
private enum class Mark { Check, Cross }

@Composable
private fun AnimatedMark(color: Color, mark: Mark, size: Dp = 44.dp) {
    val played = remember { MutableTransitionState(false).apply { targetState = true } }
    val circle by animateFloatAsState(
        if (played.targetState) 1f else 0f,
        tween(450, easing = FastOutSlowInEasing),
        label = "markCircle",
    )
    val stroke by animateFloatAsState(
        if (played.targetState) 1f else 0f,
        tween(320, delayMillis = 320, easing = FastOutSlowInEasing),
        label = "markStroke",
    )
    Canvas(Modifier.size(size)) {
        val strokeWidth = this.size.minDimension * 0.085f
        val arcStyle = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * circle,
            useCenter = false,
            style = arcStyle,
        )
        if (stroke > 0f) {
            val w = this.size.width
            val h = this.size.height
            val path = Path()
            when (mark) {
                Mark.Check -> {
                    path.moveTo(w * 0.28f, h * 0.52f)
                    path.lineTo(w * 0.45f, h * 0.68f)
                    path.lineTo(w * 0.73f, h * 0.34f)
                }
                Mark.Cross -> {
                    path.moveTo(w * 0.32f, h * 0.32f)
                    path.lineTo(w * 0.68f, h * 0.68f)
                    path.moveTo(w * 0.68f, h * 0.32f)
                    path.lineTo(w * 0.32f, h * 0.68f)
                }
            }
            val measure = PathMeasure()
            measure.setPath(path, false)
            val visible = Path()
            measure.getSegment(0f, measure.length * stroke, visible, true)
            drawPath(visible, color = color, style = arcStyle)
        }
    }
}

// ---------------------------------------------------------------------------
// Actions
// ---------------------------------------------------------------------------

/** The one strong action: full-width pill, press-scale, leading check. */
@Composable
private fun PrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "primaryScale",
    )
    Button(
        onClick = onClick,
        interactionSource = interaction,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
    }
}

/** The quiet alternative: centered text button, full-width tap target. */
@Composable
private fun QuietAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
) {
    TextButton(
        onClick = onClick,
        shape = CircleShape,
        modifier = modifier.fillMaxWidth().height(46.dp),
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Utilities
// ---------------------------------------------------------------------------

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Content that rises + fades in [delayMillis] after the dialog has landed. */
private fun Modifier.staggerIn(delayMillis: Long): Modifier = composed {
    val alpha = remember { Animatable(0f) }
    val rise = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        launch { alpha.animateTo(1f, tween(260)) }
        launch { rise.animateTo(0f, tween(340, easing = DialogEasing)) }
    }
    graphicsLayer {
        this.alpha = alpha.value
        translationY = rise.value * 14.dp.toPx()
    }
}

/** Clickable with no ripple/indication — for the scrim and to consume dialog taps. */
private fun Modifier.noRippleClick(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}
