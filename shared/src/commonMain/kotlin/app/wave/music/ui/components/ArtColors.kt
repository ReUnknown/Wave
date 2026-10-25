package app.wave.music.ui.components

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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Schemes already worked out, by art URL, so revisiting a song or page doesn't flash. */
private val schemes = app.wave.music.data.Lru<String, ColorScheme>(96)

/**
 * A dark color scheme pulled from a piece of album art, so a page can take on the colors of
 * its hero image. Falls back to [base] until the art has loaded; changes fade over a moment.
 */
@Composable
fun rememberArtColorScheme(artUrl: String?, base: ColorScheme = MaterialTheme.colorScheme): ColorScheme {
    val context = coil3.compose.LocalPlatformContext.current
    // Light and dark schemes from the same cover are different schemes.
    val dark = base.background.luminance() < 0.5f
    val key = artUrl?.let { "$it|$dark" }
    var scheme by remember(key, base) { mutableStateOf(key?.let { schemes.get(it) } ?: base) }
    LaunchedEffect(key, base) {
        if (artUrl == null || key == null || schemes.get(key) != null) return@LaunchedEffect
        val result = SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context).data(artUrl.atSize(ArtSize.THUMB)).size(128).forPixels().build(),
        )
        val image = (result as? SuccessResult)?.image ?: return@LaunchedEffect
        val (seed, mood) = withContext(Dispatchers.Default) {
            // Every pixel counts, whites and blacks too, toward how much of the cover each color covers.
            imagePixels(image)?.let { pickSeed(quantize(it)) }
        } ?: return@LaunchedEffect
        scheme = (if (dark) darkScheme(Color(seed), mood, base) else lightScheme(Color(seed), base)).also { schemes.put(key, it) }
    }
    return animateScheme(scheme)
}

/**
 * The color a cover is "about", the way Pixel picks it: the colorful family that covers the most
 * of the art, not whichever swatch happens to be most vibrant. A speck of blue on a white cover
 * covers almost nothing, so it never wins; a cover with no real color gets a neutral scheme.
 */
private fun pickSeed(swatches: List<Swatch>): Pair<Int, Float>? {
    val total = swatches.sumOf { it.population }.toFloat()
    if (total <= 0f) return null
    // How bright the cover is overall, so a dark cover gets calm colors, not neon ones.
    val mood = swatches.sumOf { (it.population * it.hsl[2]).toDouble() }.toFloat() / total
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
    return (best?.first?.rgb ?: swatches.maxByOrNull { it.population }?.rgb ?: return null) to mood
}

private fun tone(seed: Color, lightness: Float, maxSaturation: Float): Color {
    val hsl = FloatArray(3)
    colorToHsl(seed.toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtMost(maxSaturation)
    hsl[2] = lightness
    return Color(hslToColor(hsl))
}

/**
 * Dark mode: deep background, light accents. [mood] (the cover's overall brightness) calms things
 * down for dark covers: less saturated, a little dimmer, so a moody cover stays moody.
 */
private fun darkScheme(seed: Color, mood: Float, base: ColorScheme): ColorScheme {
    val calm = ((mood - 0.12f) / 0.33f).coerceIn(0f, 1f) // 0 = very dark cover, 1 = normal
    val sat = 0.48f + 0.44f * calm
    val background = tone(seed, 0.09f, 0.42f * sat)
    val onBackground = tone(seed, 0.93f, 0.3f * sat)
    return base.copy(
        primary = tone(seed, 0.6f + 0.17f * calm, 0.75f * sat),
        onPrimary = tone(seed, 0.16f, 0.85f * sat),
        primaryContainer = tone(seed, 0.25f, 0.52f * sat),
        onPrimaryContainer = tone(seed, 0.9f, 0.7f * sat),
        secondaryContainer = tone(seed, 0.2f, 0.3f * sat),
        onSecondaryContainer = tone(seed, 0.88f, 0.35f * sat),
        tertiaryContainer = tone(seed.rotateHue(40f), 0.24f, 0.4f * sat),
        onTertiaryContainer = tone(seed.rotateHue(40f), 0.9f, 0.55f * sat),
        background = background,
        onBackground = onBackground,
        surface = background,
        onSurface = onBackground,
        onSurfaceVariant = tone(seed, 0.78f, 0.22f * sat),
        surfaceContainer = Color.White.copy(alpha = 0.06f).compositeOver(background),
        surfaceContainerHigh = Color.White.copy(alpha = 0.1f).compositeOver(background),
        surfaceContainerHighest = Color.White.copy(alpha = 0.14f).compositeOver(background),
    )
}

/** Light mode: a softly tinted paper background with deeper accents. */
private fun lightScheme(seed: Color, base: ColorScheme): ColorScheme {
    val background = tone(seed, 0.965f, 0.35f)
    val onBackground = tone(seed, 0.11f, 0.3f)
    return base.copy(
        primary = tone(seed, 0.36f, 0.7f),
        onPrimary = tone(seed, 0.98f, 0.4f),
        primaryContainer = tone(seed, 0.86f, 0.6f),
        onPrimaryContainer = tone(seed, 0.16f, 0.7f),
        secondaryContainer = tone(seed, 0.9f, 0.35f),
        onSecondaryContainer = tone(seed, 0.18f, 0.4f),
        tertiaryContainer = tone(seed.rotateHue(40f), 0.88f, 0.45f),
        onTertiaryContainer = tone(seed.rotateHue(40f), 0.16f, 0.55f),
        background = background,
        onBackground = onBackground,
        surface = background,
        onSurface = onBackground,
        onSurfaceVariant = tone(seed, 0.34f, 0.18f),
        surfaceContainer = Color.Black.copy(alpha = 0.04f).compositeOver(background),
        surfaceContainerHigh = Color.Black.copy(alpha = 0.07f).compositeOver(background),
        surfaceContainerHighest = Color.Black.copy(alpha = 0.1f).compositeOver(background),
    )
}

private fun Color.rotateHue(degrees: Float): Color {
    val hsl = FloatArray(3)
    colorToHsl(toArgb(), hsl)
    hsl[0] = (hsl[0] + degrees) % 360f
    return Color(hslToColor(hsl))
}

/** Fades the colors you actually see from one song's scheme to the next. */
@Composable
internal fun animateScheme(target: ColorScheme): ColorScheme {
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
