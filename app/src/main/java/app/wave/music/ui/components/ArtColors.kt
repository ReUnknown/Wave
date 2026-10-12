package app.wave.music.ui.components

import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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

/** Schemes already worked out, by art URL, so revisiting a song or page doesn't flash. */
private val schemes = LruCache<String, ColorScheme>(96)

/**
 * A dark color scheme pulled from a piece of album art, so a page can take on the colors of
 * its hero image. Falls back to [base] until the art has loaded; changes fade over a moment.
 */
@Composable
fun rememberArtColorScheme(artUrl: String?, base: ColorScheme = MaterialTheme.colorScheme): ColorScheme {
    val context = LocalContext.current
    var scheme by remember(artUrl, base) { mutableStateOf(artUrl?.let { schemes.get(it) } ?: base) }
    LaunchedEffect(artUrl, base) {
        if (artUrl == null || schemes.get(artUrl) != null) return@LaunchedEffect
        val result = SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context).data(artUrl.atSize(ArtSize.THUMB)).size(128).allowHardware(false).build(),
        )
        val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return@LaunchedEffect
        val seed = withContext(Dispatchers.Default) {
            // No filters: whites and blacks count toward how much of the cover each color covers.
            pickSeed(Palette.from(bitmap).maximumColorCount(24).clearFilters().generate())
        } ?: return@LaunchedEffect
        scheme = schemeFromSeed(Color(seed), base).also { schemes.put(artUrl, it) }
    }
    return animate(scheme)
}

/**
 * The color a cover is "about", the way Pixel picks it: the colorful family that covers the most
 * of the art, not whichever swatch happens to be most vibrant. A speck of blue on a white cover
 * covers almost nothing, so it never wins; a cover with no real color gets a neutral scheme.
 */
private fun pickSeed(palette: Palette): Int? {
    val swatches = palette.swatches
    val total = swatches.sumOf { it.population }.toFloat()
    if (total <= 0f) return null
    fun hueGap(a: Float, b: Float) = kotlin.math.abs(a - b).let { if (it > 180f) 360f - it else it }
    fun colorful(hsl: FloatArray) = hsl[1] >= 0.22f && hsl[2] in 0.12f..0.88f
    val colorfulOnes = swatches.filter { colorful(it.hsl) }
    // How much of the cover is in this color's family (similar hue, actually colored).
    fun familyShare(hue: Float) = colorfulOnes.filter { hueGap(it.hsl[0], hue) <= 22f }.sumOf { it.population } / total
    val best = colorfulOnes
        .filter { it.hsl[1] >= 0.28f && it.hsl[2] in 0.18f..0.82f }
        .map { it to familyShare(it.hsl[0]) }
        // A family has to be a real part of the cover to set the colors.
        .filter { (_, share) -> share >= 0.08f }
        // Mostly how much of the cover it is, then how rich the color is, then how much this exact
        // shade is used (so the family's most typical shade wins over its brightest speck).
        .maxByOrNull { (swatch, share) -> share * 0.6f + swatch.hsl[1] * 0.25f + (swatch.population / total) * 0.15f }
    return best?.first?.rgb ?: palette.dominantSwatch?.rgb
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

/** Fades the colors you actually see from one song's scheme to the next. */
@Composable
private fun animate(target: ColorScheme): ColorScheme {
    val spec = tween<Color>(420, easing = FastOutSlowInEasing)
    val primary by animateColorAsState(target.primary, spec)
    val onPrimary by animateColorAsState(target.onPrimary, spec)
    val primaryContainer by animateColorAsState(target.primaryContainer, spec)
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, spec)
    val secondaryContainer by animateColorAsState(target.secondaryContainer, spec)
    val onSecondaryContainer by animateColorAsState(target.onSecondaryContainer, spec)
    val background by animateColorAsState(target.background, spec)
    val onBackground by animateColorAsState(target.onBackground, spec)
    val surface by animateColorAsState(target.surface, spec)
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, spec)
    val surfaceContainer by animateColorAsState(target.surfaceContainer, spec)
    val surfaceContainerHigh by animateColorAsState(target.surfaceContainerHigh, spec)
    val surfaceContainerHighest by animateColorAsState(target.surfaceContainerHighest, spec)
    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
    )
}
