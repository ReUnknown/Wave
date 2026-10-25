package app.wave.music.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Pixel "blue" dark scheme, used when dynamic color isn't available.
private val WaveDarkBlue = darkColorScheme(
    primary = Color(0xFFA0CAFD),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF194975),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3B4858),
    onSecondaryContainer = Color(0xFFD7E3F7),
    tertiary = Color(0xFFD6BEE4),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF523F5F),
    onTertiaryContainer = Color(0xFFF2DAFF),
    background = Color(0xFF111418),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF111418),
    onSurface = Color(0xFFE1E2E8),
    onSurfaceVariant = Color(0xFFC3C7CF),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF32353A),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
)

/** The dark scheme, even in light mode: Now Playing stays dark and immersive over the art. */
@Composable
fun darkBaseScheme(): androidx.compose.material3.ColorScheme =
    app.wave.music.platform.systemColorScheme(dark = true) ?: WaveDarkBlue

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WaveTheme(content: @Composable () -> Unit) {
    // Follows the phone: light mode when the system is light.
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val colorScheme = app.wave.music.platform.systemColorScheme(dark)
        ?: if (dark) WaveDarkBlue else androidx.compose.material3.lightColorScheme()
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = WaveTypography,
        content = content,
    )
}
