package tech.ssemaj.pipe.samplesolo

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Teal-branded fallback matching the flagship samples' design language. */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF00696D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF2EF),
    onPrimaryContainer = Color(0xFF002021),
    secondary = Color(0xFF4A6365),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E9),
    onSecondaryContainer = Color(0xFF051F21),
    tertiary = Color(0xFF4C5F7C),
    onTertiary = Color.White,
    background = Color(0xFFF9FAF8),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFF9FAF8),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDAE4E5),
    onSurfaceVariant = Color(0xFF3F4949),
    outline = Color(0xFF6F797A),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF80D5D2),
    onPrimary = Color(0xFF003738),
    primaryContainer = Color(0xFF004F51),
    onPrimaryContainer = Color(0xFF9CF2EF),
    secondary = Color(0xFFB0CBCB),
    onSecondary = Color(0xFF1C3536),
    tertiary = Color(0xFFB3C8E8),
    onTertiary = Color(0xFF1D314B),
    background = Color(0xFF191C1C),
    onBackground = Color(0xFFE0E3E2),
    surface = Color(0xFF191C1C),
    onSurface = Color(0xFFE0E3E2),
    surfaceVariant = Color(0xFF3F4949),
    onSurfaceVariant = Color(0xFFBEC8C9),
    outline = Color(0xFF889393),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val SoloShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SoloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        shapes = SoloShapes,
        content = content,
    )
}
