package app.wave.music.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/*
 * Wave's blur, built on RenderEffect (Android 12+), no library.
 *
 * Progressive blur = several copies of the content, each blurred harder than the last, each
 * masked (after blurring) so it only shows where the blur should be at least that strong.
 * Stacked, they read as one continuous blur that ramps smoothly, like iOS.
 *
 * Performance: a blur's cost grows with the pixels it covers and its radius. A blurred copy has
 * no fine detail left to lose, so each one is drawn at a fraction of the screen's resolution
 * (with the radius shrunk to match) and scaled up. The stronger the blur, the smaller the copy:
 * up to 1/36 of the pixels, with no visible difference. Images for blurred copies are decoded at
 * that small size too.
 */

/** Blur strengths, weakest → strongest, as a fraction of the maximum radius. */
private val FINE_STEPS = listOf(0.06f, 0.14f, 0.26f, 0.42f, 0.62f, 0.82f, 1f)

/** Fewer, wider steps: on big computer windows each step is a full-window pass, so they're rationed. */
private val LIGHT_STEPS = listOf(0.12f, 0.4f, 1f)

private val STEPS get() = if (BlurBudget.light) LIGHT_STEPS else FINE_STEPS

/** How many layers progressive blurs use; computers ask for the light version. */
object BlurBudget {
    var light = false
}

private fun Modifier.maskedBy(brush: Brush): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(brush, blendMode = BlendMode.DstIn)
    }

/** How much smaller to draw a copy blurred by [radiusPx]: about one pixel per 6px of blur. */
private fun downscaleFor(radiusPx: Float) = (radiusPx / 6f).coerceIn(1f, 6f)

/**
 * Draws [content] blurred by [radius] (and optionally masked), but at reduced resolution:
 * laid out 1/f the size, blurred by radius/f, then scaled back up by f.
 */
@Composable
private fun LowResBlur(
    radius: Dp,
    modifier: Modifier = Modifier,
    mask: Brush? = null,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.clipToBounds()) {
        val density = LocalDensity.current
        val radiusPx = with(density) { radius.toPx() }
        // Unbounded space (shouldn't happen for a backdrop) falls back to full resolution.
        val bounded = constraints.hasBoundedWidth && constraints.hasBoundedHeight
        val f = if (bounded) downscaleFor(radiusPx) else 1f
        if (!bounded) {
            Box(
                Modifier
                    .then(if (mask != null) Modifier.maskedBy(mask) else Modifier)
                    .graphicsLayer { if (radiusPx > 0f) renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Clamp) },
            ) { content() }
            return@BoxWithConstraints
        }
        // Rounded up: a copy a pixel short would leave a gap (up to f pixels once scaled back up)
        // along its right and bottom edges, which shows as the blur cutting off.
        val w = with(density) { kotlin.math.ceil(constraints.maxWidth / f).toDp() }
        val h = with(density) { kotlin.math.ceil(constraints.maxHeight / f).toDp() }
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(w, h)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = f
                    scaleY = f
                }
                .then(if (mask != null) Modifier.maskedBy(mask) else Modifier)
                .graphicsLayer {
                    val r = radiusPx / f
                    if (r > 0f) renderEffect = BlurEffect(r, r, TileMode.Clamp)
                },
        ) { content() }
    }
}

/**
 * Album art that's sharp at the top and blurs progressively toward the bottom: no blur above
 * [start], full [maxRadius] at [end] and below (fractions of the height).
 */
@Composable
fun ProgressiveBlurImage(
    model: Any?,
    modifier: Modifier = Modifier,
    start: Float = 0.4f,
    end: Float = 0.95f,
    maxRadius: Dp = 64.dp,
) {
    ProgressiveBlur(modifier, start, end, maxRadius) {
        AsyncImage(model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/**
 * Any [content], sharp above [start] and blurring progressively to [maxRadius] at [end] (fractions
 * of the height). Everything is blurred as one picture, so parts of it never meet at a seam.
 */
@Composable
fun ProgressiveBlur(
    modifier: Modifier = Modifier,
    start: Float = 0.4f,
    end: Float = 0.95f,
    maxRadius: Dp = 64.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier.clipToBounds()) {
        content()
        val span = end - start
        STEPS.forEachIndexed { i, step ->
            // Each stronger copy fades in a little lower down.
            val from = start + span * (i.toFloat() / STEPS.size)
            val to = start + span * ((i + 1f) / STEPS.size)
            LowResBlur(
                radius = maxRadius * step,
                modifier = Modifier.fillMaxSize(),
                mask = Brush.verticalGradient(0f to Color.Transparent, from to Color.Transparent, to to Color.Black, 1f to Color.Black),
                content = content,
            )
        }
    }
}

/**
 * The whole image blurred evenly (drawn small and scaled up, so it's cheap). [fadeBottom] fades
 * out that fraction of its height, for backdrops that shouldn't end in a hard edge.
 */
@Composable
fun BlurredImage(model: Any?, modifier: Modifier = Modifier, radius: Dp = 64.dp, fadeBottom: Float = 0f) {
    val fade = if (fadeBottom > 0f) {
        Modifier.maskedBy(Brush.verticalGradient(0f to Color.Black, (1f - fadeBottom) to Color.Black, 1f to Color.Transparent))
    } else Modifier
    LowResBlur(radius, modifier.then(fade)) {
        AsyncImage(model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/**
 * What a [ProgressiveBackdropBlur] blurs: the content underneath records itself into [layer]
 * through [backdropSource]. It only redraws when that content actually changes (scrolling,
 * an animation), never on an idle screen.
 */
@Stable
class Backdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var version by mutableIntStateOf(0)
    private var count = 0

    /** Write-only bump, so the drawing that calls it doesn't start observing it. */
    internal fun changed() {
        version = ++count
    }
}

@Composable
fun rememberBackdrop(): Backdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer) }
}

/**
 * The content underneath records itself into [backdrop]; a [ProgressiveBackdropBlur] over it
 * redraws that blurred. Both must share the same top-left origin (siblings filling one Box), and
 * the blur must not be inside the source.
 */
fun Modifier.backdropSource(backdrop: Backdrop): Modifier = drawWithContent {
    backdrop.layer.record { this@drawWithContent.drawContent() }
    drawLayer(backdrop.layer)
    backdrop.changed()
}

/**
 * Frosted header, iOS style: strongest blur at the very top edge, easing off continuously
 * down to sharp at the bottom of this area.
 */
@Composable
fun ProgressiveBackdropBlur(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    maxRadius: Dp = 64.dp,
) {
    Box(modifier.clipToBounds()) {
        STEPS.forEachIndexed { i, step ->
            // Strongest copies only cover the top; weaker ones reach further down.
            val solidUntil = 1f - (i + 1f) / STEPS.size
            val goneAt = (solidUntil + 1f / STEPS.size + 0.12f).coerceAtMost(1f)
            LowResBlur(
                radius = maxRadius * step,
                modifier = Modifier.fillMaxSize(),
                mask = Brush.verticalGradient(0f to Color.Black, solidUntil to Color.Black, goneAt to Color.Transparent),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            backdrop.version // redraw when what's underneath changes
                            // The source is full size; this copy is drawn smaller.
                            val f = backdrop.layer.size.width.let { if (it > 0) it / size.width else 1f }
                            scale(1f / f, 1f / f, pivot = androidx.compose.ui.geometry.Offset.Zero) { drawLayer(backdrop.layer) }
                        },
                )
            }
        }
    }
}
