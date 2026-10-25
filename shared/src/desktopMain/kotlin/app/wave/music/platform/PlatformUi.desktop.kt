package app.wave.music.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** Computers have no wallpaper colors to borrow: Wave's own blue scheme is used. */
@Composable
actual fun systemColorScheme(dark: Boolean): ColorScheme? = null
