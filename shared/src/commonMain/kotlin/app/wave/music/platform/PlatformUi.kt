package app.wave.music.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** The system's own colors (Android 12+ wallpaper colors), or null where the platform has none. */
@Composable
expect fun systemColorScheme(dark: Boolean): ColorScheme?

/** Prisma's type: the phone's own Google Sans where it has it (Pixels), the default font otherwise. */
expect val prismaDisplayFont: androidx.compose.ui.text.font.FontFamily
expect val prismaTextFont: androidx.compose.ui.text.font.FontFamily
