package app.wave.music.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** The system's own colors (Android 12+ wallpaper colors), or null where the platform has none. */
@Composable
expect fun systemColorScheme(dark: Boolean): ColorScheme?

/** Prisma's type: the phone's own Google Sans where it has it (Pixels), the default font otherwise. */
expect val prismaDisplayFont: androidx.compose.ui.text.font.FontFamily
expect val prismaTextFont: androidx.compose.ui.text.font.FontFamily


/** Prisma's look for this session: Legacy swaps in Wave 1.0's touches. Snapshot state, so reading it recomposes. */
object PrismaLook {
    var legacy by androidx.compose.runtime.mutableStateOf(false)
    /** Frosted glass: 0 off, 1 light, 2 full. */
    var blur by androidx.compose.runtime.mutableIntStateOf(2)
    /** Corners: 0 rounded, 1 squircle, 2 sharp. */
    var corners by androidx.compose.runtime.mutableIntStateOf(0)
    var dense by androidx.compose.runtime.mutableStateOf(false)

    /** A corner radius in the chosen style (and squarer in Legacy). */
    fun corner(base: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp = base * when {
        corners == 2 -> 0.35f
        corners == 1 || legacy -> 0.7f
        else -> 1f
    }
}
