package app.wave.music.platform

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun systemColorScheme(dark: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

private fun deviceFamily(name: String) = androidx.compose.ui.text.font.FontFamily(
    listOf(
        androidx.compose.ui.text.font.FontWeight.Normal,
        androidx.compose.ui.text.font.FontWeight.Medium,
        androidx.compose.ui.text.font.FontWeight.SemiBold,
        androidx.compose.ui.text.font.FontWeight.Bold,
        androidx.compose.ui.text.font.FontWeight.ExtraBold,
    ).map { w -> androidx.compose.ui.text.font.Font(androidx.compose.ui.text.font.DeviceFontFamilyName(name), w) },
)

actual val prismaDisplayFont: androidx.compose.ui.text.font.FontFamily = deviceFamily("google-sans")
actual val prismaTextFont: androidx.compose.ui.text.font.FontFamily = deviceFamily("google-sans-text")
