package tech.ssemaj.pipe.samplesolo

import android.app.Application
import android.os.Bundle
import android.os.Process
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.provider.HostHandle
import tech.ssemaj.pipe.provider.PipeContent
import tech.ssemaj.pipe.provider.PaneResult
import tech.ssemaj.pipe.provider.PaneSpec
import tech.ssemaj.pipe.provider.PipeProviderService

private val BandEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

private val BandScheme = darkColorScheme(
    surface = Color(0xFF16191B),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xCCFFFFFF),
)

/**
 * Experiment: N processes, each **independently interactive**, in one host window. Each subclass
 * runs in its own process and draws a rounded band anchored to [gravity]. The pane window is sized
 * to the band and made non-touch-modal ([PaneSpec.touchModal] = false), so touches outside the
 * band pass through to the panes/host behind it — every band works, not just the top one's. Each
 * band counts its own taps in its own process and reports the count to the host over the channel,
 * and prints its UI-thread name + kernel tid so you can see the threads (and processes) are
 * genuinely distinct.
 */
abstract class RegionPaneService : PipeProviderService() {
    abstract val letter: Char
    abstract val gravity: Int

    /** Top and bottom stops of the band's gradient. */
    abstract val gradient: List<Color>

    override suspend fun onOpenPane(request: PipeRequest, host: HostHandle): PaneResult {
        val view = ComposeView(this).apply {
            setContent {
                MaterialTheme(colorScheme = BandScheme) {
                    Band(
                        letter = letter,
                        gradient = gradient,
                        fromTop = gravity == Gravity.TOP,
                        fromBottom = gravity == Gravity.BOTTOM,
                        processName = Application.getProcessName(),
                        pid = Process.myPid(),
                        tid = Process.myTid(),
                        onTap = { taps ->
                            paneScope.launch {
                                host.send(PipeMessage(Bundle().apply {
                                    putString("band", letter.toString())
                                    putInt("taps", taps)
                                }))
                            }
                        },
                    )
                }
            }
        }

        // Window sized to this band, non-touch-modal → independently interactive; touches
        // elsewhere fall through to the other panes / the host.
        val spec = PaneSpec(
            focusable = false,
            touchModal = false,
            gravity = gravity,
            heightPx = WindowManager.LayoutParams.WRAP_CONTENT,
        )
        return PaneResult.Content(object : PipeContent { override val view: View = view }, spec)
    }
}

@Composable
private fun Band(
    letter: Char,
    gradient: List<Color>,
    fromTop: Boolean,
    fromBottom: Boolean,
    processName: String,
    pid: Int,
    tid: Int,
    onTap: (Int) -> Unit,
) {
    var taps by remember { mutableIntStateOf(0) }
    val pulse = remember { Animatable(1f) }
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    val enter = when {
        fromTop -> slideInVertically(tween(420, easing = BandEasing)) { -it } + fadeIn(tween(240))
        fromBottom -> slideInVertically(tween(420, easing = BandEasing)) { it } + fadeIn(tween(240))
        else -> scaleIn(initialScale = 0.92f, animationSpec = tween(380, easing = BandEasing)) + fadeIn(tween(240))
    }

    // Transparent padding around the band = the band floats over the host with real margins;
    // touches on the margins pass through (non-touch-modal window).
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        AnimatedVisibility(visibleState = shown, enter = enter) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value },
            ) {
                Column(
                    Modifier
                        .background(Brush.linearGradient(gradient))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            taps++
                            onTap(taps)
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "PANE $letter",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        TapCounter(taps)
                    }
                    Text(
                        "$processName · pid $pid · ui tid $tid",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xCCFFFFFF),
                    )
                    Text(
                        "tap this band — it counts here, in its own process, and tells the host",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xB3FFFFFF),
                    )
                }
            }
        }
    }

    // Springy pop on each tap (bigger for milestones).
    LaunchedEffect(taps) {
        if (taps > 0) {
            val target = if (taps % 5 == 0) 1.08f else 1.03f
            pulse.snapTo(target)
            pulse.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
}

@Composable
private fun TapCounter(taps: Int) {
    val scale by animateFloatAsState(
        if (taps == 0) 1f else 1.06f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "counterScale",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(Color(0x33FFFFFF), CircleShape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            "$taps ×",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

class PaneServiceA : RegionPaneService() {
    override val letter = 'A'
    override val gravity = Gravity.TOP
    override val gradient = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
}

class PaneServiceB : RegionPaneService() {
    override val letter = 'B'
    override val gravity = Gravity.CENTER_VERTICAL
    override val gradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
}

class PaneServiceC : RegionPaneService() {
    override val letter = 'C'
    override val gravity = Gravity.BOTTOM
    override val gradient = listOf(Color(0xFF10B981), Color(0xFF047857))
}
