package app.wave.music.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/*
 * Wave's blur, built on RenderEffect (Android 12+), no library.
 *
 * Progressive blur = several copies of the content, each blurred harder than the last, each
 * masked (after blurring) so it only shows where the blur should be at least that strong.
 * Stacked, they read as one continuous blur that ramps smoothly, like iOS.
 */

/** Blur strengths, weakest → strongest, as a fraction of the maximum radius. */
private val STEPS = listOf(0.06f, 0.14f, 0.26f, 0.42f, 0.62f, 0.82f, 1f)

private fun Modifier.maskedBy(brush: Brush): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(brush, blendMode = BlendMode.DstIn)
    }

private fun Modifier.blurredBy(radius: Dp): Modifier = graphicsLayer {
    val r = radius.toPx()
    if (r > 0f) renderEffect = BlurEffect(r, r, TileMode.Clamp)
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
    Box(modifier.clipToBounds()) {
        AsyncImage(model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        val span = end - start
        STEPS.forEachIndexed { i, step ->
            // Each stronger copy fades in a little lower down.
            val from = start + span * (i.toFloat() / STEPS.size)
            val to = start + span * ((i + 1f) / STEPS.size)
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .maskedBy(Brush.verticalGradient(0f to Color.Transparent, from to Color.Transparent, to to Color.Black, 1f to Color.Black))
                    .blurredBy(maxRadius * step),
            )
        }
    }
}

/** The whole image blurred evenly. */
@Composable
fun BlurredImage(model: Any?, modifier: Modifier = Modifier, radius: Dp = 64.dp) {
    AsyncImage(model, null, modifier.blurredBy(radius), contentScale = ContentScale.Crop)
}

/**
 * The content underneath records itself into [layer] ([backdropSource]); a
 * [ProgressiveBackdropBlur] over it redraws that layer blurred. Both must share the same
 * top-left origin (siblings filling one Box), and the blur must not be inside the source.
 */
fun Modifier.backdropSource(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

/**
 * Frosted header, iOS style: strongest blur at the very top edge, easing off continuously
 * down to sharp at the bottom of this area.
 */
@Composable
fun ProgressiveBackdropBlur(
    layer: GraphicsLayer,
    modifier: Modifier = Modifier,
    maxRadius: Dp = 64.dp,
) {
    // Redraw every frame so the blur follows scrolling and animation underneath it.
    val frame by produceState(0L) {
        while (true) withFrameMillis { value = it }
    }
    Box(modifier.clipToBounds()) {
        STEPS.forEachIndexed { i, step ->
            // Strongest copies only cover the top; weaker ones reach further down.
            val solidUntil = 1f - (i + 1f) / STEPS.size
            val goneAt = (solidUntil + 1f / STEPS.size + 0.12f).coerceAtMost(1f)
            Box(
                Modifier
                    .fillMaxSize()
                    .maskedBy(Brush.verticalGradient(0f to Color.Black, solidUntil to Color.Black, goneAt to Color.Transparent)),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .blurredBy(maxRadius * step)
                        .drawBehind {
                            frame // read so this redraws each frame
                            drawLayer(layer)
                        },
                )
            }
        }
    }
}
