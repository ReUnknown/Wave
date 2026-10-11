package app.wave.music.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Wave's progress line: the played part is a travelling wave while music plays and springs flat
 * when paused. Optionally draggable to seek.
 */
@Composable
fun WavyProgress(
    progress: Float,
    playing: Boolean,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 3.dp,
    amplitude: Dp = 3.dp,
    wavelength: Dp = 22.dp,
    showThumb: Boolean = false,
    onSeek: ((Float) -> Unit)? = null,
    /** 0..1 loudness; the wave grows with it when "Move with the music" is on. */
    energy: () -> Float = { -1f },
) {
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "phase",
    )
    val waveScale by animateFloatAsState(if (playing) 1f else 0f, label = "amp")
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragFraction else progress.coerceIn(0f, 1f)

    var input = modifier
    if (onSeek != null) {
        input = input
            .pointerInput(Unit) {
                detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        dragFraction = (it.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragging = false
                        onSeek(dragFraction)
                    },
                    onDragCancel = { dragging = false },
                ) { change, _ -> dragFraction = (change.position.x / size.width).coerceIn(0f, 1f) }
            }
    }

    Canvas(input) {
        val w = size.width
        val cy = size.height / 2
        val strokePx = stroke.toPx()
        val e = energy()
        val amp = amplitude.toPx() * waveScale * (if (e < 0f) 1f else 0.55f + e * 0.9f)
        val wl = wavelength.toPx()
        val gap = if (showThumb) 8.dp.toPx() else 4.dp.toPx()
        val x = (w * shown).coerceIn(strokePx, w)

        if (x > strokePx) {
            val path = Path().apply {
                var px = strokePx / 2
                moveTo(px, cy + amp * sin(2 * PI * (px / wl - phase)).toFloat())
                while (px < x) {
                    px = (px + 2f).coerceAtMost(x)
                    lineTo(px, cy + amp * sin(2 * PI * (px / wl - phase)).toFloat())
                }
            }
            drawPath(path, color, style = Stroke(width = strokePx, cap = StrokeCap.Round))
        }
        val trackStart = x + gap
        if (trackStart < w) {
            drawLine(trackColor, Offset(trackStart, cy), Offset(w - strokePx / 2, cy), strokePx, StrokeCap.Round)
        }
        if (showThumb) {
            val tw = 5.dp.toPx()
            val th = 24.dp.toPx()
            drawRoundRect(color, Offset(x - tw / 2, cy - th / 2), Size(tw, th), CornerRadius(tw / 2))
        }
    }
}
