package app.wave.music.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A dark color scheme pulled from a piece of album art, so a page can take on the colors of
 * its hero image. Falls back to [base] until the art has loaded.
 */
@Composable
fun rememberArtColorScheme(artUrl: String?, base: ColorScheme = MaterialTheme.colorScheme): ColorScheme {
    val context = LocalContext.current
    var scheme by remember(artUrl, base) { mutableStateOf(base) }
    LaunchedEffect(artUrl, base) {
        if (artUrl == null) return@LaunchedEffect
        val result = SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context).data(artUrl).size(128).allowHardware(false).build(),
        )
        val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return@LaunchedEffect
        val palette = withContext(Dispatchers.Default) { Palette.from(bitmap).generate() }
        val seed = palette.vibrantSwatch?.rgb ?: palette.dominantSwatch?.rgb ?: return@LaunchedEffect
        scheme = schemeFromSeed(Color(seed), base)
    }
    return scheme
}

private fun tone(seed: Color, lightness: Float, maxSaturation: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtMost(maxSaturation)
    hsl[2] = lightness
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun schemeFromSeed(seed: Color, base: ColorScheme): ColorScheme {
    val background = tone(seed, 0.09f, 0.45f)
    val onBackground = tone(seed, 0.93f, 0.35f)
    return base.copy(
        primary = tone(seed, 0.78f, 0.85f),
        onPrimary = tone(seed, 0.16f, 0.9f),
        primaryContainer = tone(seed, 0.26f, 0.6f),
        onPrimaryContainer = tone(seed, 0.9f, 0.8f),
        secondaryContainer = tone(seed, 0.2f, 0.35f),
        onSecondaryContainer = tone(seed, 0.88f, 0.4f),
        tertiaryContainer = tone(seed.rotateHue(40f), 0.24f, 0.45f),
        onTertiaryContainer = tone(seed.rotateHue(40f), 0.9f, 0.6f),
        background = background,
        onBackground = onBackground,
        surface = background,
        onSurface = onBackground,
        onSurfaceVariant = tone(seed, 0.78f, 0.25f),
        surfaceContainer = Color.White.copy(alpha = 0.06f).compositeOver(background),
        surfaceContainerHigh = Color.White.copy(alpha = 0.1f).compositeOver(background),
        surfaceContainerHighest = Color.White.copy(alpha = 0.14f).compositeOver(background),
    )
}

private fun Color.rotateHue(degrees: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[0] = (hsl[0] + degrees) % 360f
    return Color(ColorUtils.HSLToColor(hsl))
}
