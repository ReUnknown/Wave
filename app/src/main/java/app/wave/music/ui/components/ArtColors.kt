package app.wave.music.ui.components

import android.util.LruCache
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.materialkolor.hct.Hct
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.score.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Schemes already worked out, by art URL, so revisiting a song or page doesn't flash. */
private val schemes = LruCache<String, ColorScheme>(96)

/**
 * A dark color scheme pulled from a piece of album art, picked the way a Pixel colors its media
 * player: quantize the art (Celebi), score the colors for how well they'd work as a theme
 * (vivid, common, not muddy), and build a "content" scheme that stays faithful to the winner.
 * Falls back to [base] until the art has loaded; changes fade over a moment.
 */
@Composable
fun rememberArtColorScheme(artUrl: String?, base: ColorScheme = MaterialTheme.colorScheme): ColorScheme {
    val context = LocalContext.current
    var scheme by remember(artUrl, base) { mutableStateOf(artUrl?.let { schemes.get(it) } ?: base) }
    LaunchedEffect(artUrl, base) {
        if (artUrl == null || schemes.get(artUrl) != null) return@LaunchedEffect
        val result = SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context).data(artUrl).size(112).allowHardware(false).build(),
        )
        val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return@LaunchedEffect
        scheme = withContext(Dispatchers.Default) {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val seed = Score.score(QuantizerCelebi.quantize(pixels, 128), 4, GOOGLE_BLUE, true).first()
            SchemeContent(Hct.fromInt(seed), true, 0.0).toColorScheme(base)
        }.also { schemes.put(artUrl, it) }
    }
    return animate(scheme)
}

private const val GOOGLE_BLUE = 0xFF4285F4.toInt()

private fun DynamicScheme.toColorScheme(base: ColorScheme): ColorScheme = base.copy(
    primary = Color(primary),
    onPrimary = Color(onPrimary),
    primaryContainer = Color(primaryContainer),
    onPrimaryContainer = Color(onPrimaryContainer),
    inversePrimary = Color(inversePrimary),
    secondary = Color(secondary),
    onSecondary = Color(onSecondary),
    secondaryContainer = Color(secondaryContainer),
    onSecondaryContainer = Color(onSecondaryContainer),
    tertiary = Color(tertiary),
    onTertiary = Color(onTertiary),
    tertiaryContainer = Color(tertiaryContainer),
    onTertiaryContainer = Color(onTertiaryContainer),
    background = Color(background),
    onBackground = Color(onBackground),
    surface = Color(surface),
    onSurface = Color(onSurface),
    surfaceVariant = Color(surfaceVariant),
    onSurfaceVariant = Color(onSurfaceVariant),
    surfaceTint = Color(surfaceTint),
    inverseSurface = Color(inverseSurface),
    inverseOnSurface = Color(inverseOnSurface),
    outline = Color(outline),
    outlineVariant = Color(outlineVariant),
    surfaceBright = Color(surfaceBright),
    surfaceDim = Color(surfaceDim),
    surfaceContainerLowest = Color(surfaceContainerLowest),
    surfaceContainerLow = Color(surfaceContainerLow),
    surfaceContainer = Color(surfaceContainer),
    surfaceContainerHigh = Color(surfaceContainerHigh),
    surfaceContainerHighest = Color(surfaceContainerHighest),
)

/** Fades the colors you actually see from one song's scheme to the next. */
@Composable
private fun animate(target: ColorScheme): ColorScheme {
    val spec = tween<Color>(600)
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
