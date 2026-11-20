package app.wave.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import app.wave.music.platform.PrismaLook
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Prisma's frosted glass, following Customize › Blur: full, light (less blur, a little more
 * tint), or off (a solid [solid] fill, fading out too when [progressive]).
 */
fun Modifier.glass(
    state: HazeState?,
    radius: Dp,
    background: Color,
    tints: List<HazeTint>,
    solid: Color,
    progressive: Boolean = false,
): Modifier {
    val level = PrismaLook.blur
    if (state == null || level == 0) {
        return if (progressive) background(Brush.verticalGradient(0f to solid, 0.6f to solid.copy(alpha = solid.alpha * 0.7f), 1f to Color.Transparent))
        else background(solid)
    }
    return hazeEffect(state) {
        blurRadius = if (level == 1) radius * 0.45f else radius
        backgroundColor = background
        this.tints = if (level == 1) tints + HazeTint(solid.copy(alpha = 0.25f)) else tints
        noiseFactor = 0f
        if (progressive) this.progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f, preferPerformance = true)
    }
}
