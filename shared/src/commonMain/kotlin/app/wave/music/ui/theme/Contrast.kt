package app.wave.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import app.wave.music.platform.PrismaLook

/** How readable [fg] is on [bg]: 1 (invisible) to 21 (black on white). 4.5 is the bar for text. */
fun contrast(fg: Color, bg: Color): Float {
    val a = fg.compositeOver(bg).luminance() + 0.05f
    val b = bg.luminance() + 0.05f
    return if (a > b) a / b else b / a
}

/** [fg] nudged toward white or black (whichever reads on [bg]) until it reaches [ratio]. */
fun readable(fg: Color, bg: Color, ratio: Float): Color {
    if (contrast(fg, bg) >= ratio) return fg
    val toward = if (bg.luminance() < 0.5f) Color.White else Color.Black
    var t = 0.1f
    while (t <= 1f) {
        val c = androidx.compose.ui.graphics.lerp(fg, toward, t).copy(alpha = 1f)
        if (contrast(c, bg) >= ratio) return c
        t += 0.1f
    }
    return toward
}

/**
 * Accessibility › High contrast and automatic contrast, applied to every color scheme Wave uses
 * (the app's and each song's). Automatic contrast only steps in where text would be hard to read;
 * high contrast goes all the way: black or white backgrounds, white or black text, bold accents.
 */
fun accessible(scheme: ColorScheme): ColorScheme {
    val dark = scheme.background.luminance() < 0.5f
    if (PrismaLook.highContrast) {
        val bg = if (dark) Color.Black else Color.White
        val ink = if (dark) Color.White else Color.Black
        val primary = readable(scheme.primary, bg, 7f)
        val onPrimary = if (primary.luminance() > 0.4f) Color.Black else Color.White
        val raised = { level: Float -> (if (dark) Color.White else Color.Black).copy(alpha = level).compositeOver(bg) }
        return scheme.copy(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primary, onPrimaryContainer = onPrimary,
            secondaryContainer = raised(0.16f), onSecondaryContainer = ink,
            tertiaryContainer = raised(0.16f), onTertiaryContainer = ink,
            background = bg, onBackground = ink, surface = bg, onSurface = ink, onSurfaceVariant = ink,
            surfaceContainerLowest = bg, surfaceContainerLow = raised(0.06f), surfaceContainer = raised(0.1f),
            surfaceContainerHigh = raised(0.14f), surfaceContainerHighest = raised(0.18f),
            outline = ink.copy(alpha = 0.8f), outlineVariant = ink.copy(alpha = 0.5f),
        )
    }
    if (!PrismaLook.autoContrast) return scheme
    return scheme.copy(
        onBackground = readable(scheme.onBackground, scheme.background, 7f),
        onSurface = readable(scheme.onSurface, scheme.surface, 7f),
        onSurfaceVariant = readable(scheme.onSurfaceVariant, scheme.surface, 4.5f),
        primary = readable(scheme.primary, scheme.background, 3f),
        onPrimary = readable(scheme.onPrimary, scheme.primary, 4.5f),
        onPrimaryContainer = readable(scheme.onPrimaryContainer, scheme.primaryContainer, 4.5f),
        onSecondaryContainer = readable(scheme.onSecondaryContainer, scheme.secondaryContainer, 4.5f),
    )
}
