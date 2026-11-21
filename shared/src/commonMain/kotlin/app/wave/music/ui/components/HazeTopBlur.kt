package app.wave.music.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Frosted glass across the top of a page: the content behind it blurred smoothly, strongest at
 * the very top and fading to sharp at the bottom edge, so there's no line where it ends.
 */
@Composable
fun HazeTopBlur(state: HazeState, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier.hazeEffect(state) {
            blurRadius = 26.dp
            backgroundColor = c.background
            tints = listOf(HazeTint(c.background.copy(alpha = 0.35f)))
            noiseFactor = 0f
            progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f, preferPerformance = true)
        },
    )
}
