package app.wave.music.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.gestures.draggable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.data.WaveSettings
import app.wave.music.playback.PlayerConnection
import app.wave.music.ui.components.pressable

private val Speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private fun speedLabel(s: Float) = if (s == 1f) "1×" else "${s.toString().trimEnd('0').trimEnd('.')}×"

/**
 * The player's extra controls: Wave's own volume (separate from the device's, like YouTube's
 * slider) and playback speed. Phones reveal it by swiping up on the tray; big screens show it
 * under the controls.
 */
@Composable
fun PlaybackExtras(speed: Float, ink: Color, accent: Color, onAccent: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BigVolume(ink, accent, onAccent)
        Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Speeds.forEachIndexed { i, s ->
                val on = kotlin.math.abs(speed - s) < 0.01f
                val fill by animateColorAsState(if (on) accent else ink.copy(alpha = 0.1f), tween(160), label = "speedFill")
                val text by animateColorAsState(if (on) onAccent else ink, tween(160), label = "speedInk")
                val outer by animateDpAsState(if (on) 20.dp else 8.dp, app.wave.music.ui.components.WaveMotion.bouncy(), label = "speedShape")
                val shape = when (i) {
                    0 -> RoundedCornerShape(20.dp, outer, outer, 20.dp)
                    Speeds.lastIndex -> RoundedCornerShape(outer, 20.dp, 20.dp, outer)
                    else -> RoundedCornerShape(outer)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressable(shape, fill, pressedScale = 0.92f) { PlayerConnection.setSpeed(s) }
                        .semantics { contentDescription = "Speed ${speedLabel(s)}" },
                    contentAlignment = Alignment.Center,
                ) { Text(speedLabel(s), color = text, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, fontSize = 14.sp) }
            }
        }
    }
}

/** A chunky volume bar, Pixel style: the filled part carries the speaker icon. */
@Composable
private fun BigVolume(ink: Color, accent: Color, onAccent: Color) {
    val level = WaveSettings.state.collectAsState().value.appVolume
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .semantics { contentDescription = "Wave volume ${(level * 100).toInt()}%" }
            .volumeInput()
            .drawBehind {
                drawRoundRect(ink.copy(alpha = 0.1f), cornerRadius = CornerRadius(size.height / 2))
                val w = (size.width * level).coerceAtLeast(size.height)
                drawRoundRect(accent, size = Size(w, size.height), cornerRadius = CornerRadius(size.height / 2))
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Icon(
            when {
                level == 0f -> Icons.AutoMirrored.Rounded.VolumeOff
                level < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                else -> Icons.AutoMirrored.Rounded.VolumeUp
            },
            null,
            tint = onAccent,
            modifier = Modifier.padding(start = 12.dp).size(24.dp),
        )
        Text(
            "${(level * 100).toInt()}%",
            Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            color = if (level > 0.85f) onAccent else ink.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

/** A slim volume line with a knob, for the computer player bar. */
@Composable
fun SlimVolume(color: Color, modifier: Modifier = Modifier) {
    val level = WaveSettings.state.collectAsState().value.appVolume
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(36.dp)
                .pressable(androidx.compose.foundation.shape.CircleShape, pressedScale = 0.86f) {
                    // Click the speaker to mute, again to bring it back.
                    PlayerConnection.setVolume(if (level > 0f) 0f else 0.7f, done = true)
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when {
                    level == 0f -> Icons.AutoMirrored.Rounded.VolumeOff
                    level < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                    else -> Icons.AutoMirrored.Rounded.VolumeUp
                },
                if (level == 0f) "Unmute" else "Mute",
                tint = color,
                modifier = Modifier.size(22.dp),
            )
        }
        Box(
            Modifier
                .padding(start = 6.dp)
                .width(104.dp)
                .height(28.dp)
                .semantics { contentDescription = "Wave volume ${(level * 100).toInt()}%" }
                .volumeInput()
                .drawBehind {
                    val cy = size.height / 2
                    val track = 4.dp.toPx()
                    drawLine(color.copy(alpha = 0.25f), Offset(0f, cy), Offset(size.width, cy), track, StrokeCap.Round)
                    drawLine(color, Offset(0f, cy), Offset(size.width * level, cy), track, StrokeCap.Round)
                    drawCircle(color, 6.dp.toPx(), Offset(size.width * level, cy))
                },
        )
    }
}

/** Tap, drag or scroll the mouse wheel over it to set Wave's volume. */
private fun Modifier.volumeInput(): Modifier = this
    .pointerInput(Unit) {
        detectTapGestures { PlayerConnection.setVolume(it.x / size.width, done = true) }
    }
    .pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = { PlayerConnection.setVolume(WaveSettings.state.value.appVolume, done = true) },
        ) { change, _ ->
            change.consume()
            PlayerConnection.setVolume(change.position.x / size.width, done = false)
        }
    }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Scroll) {
                    val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (dy != 0f) {
                        PlayerConnection.setVolume(WaveSettings.state.value.appVolume - dy * 0.05f, done = true)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
    }

/** How far the extras are pulled out (0 hidden … 1 shown); follows your finger, then settles. */
class Reveal internal constructor(private val scope: kotlinx.coroutines.CoroutineScope) {
    var fraction by androidx.compose.runtime.mutableFloatStateOf(0f)
        internal set
    internal var height = 1
    private var settling: kotlinx.coroutines.Job? = null

    val open get() = fraction > 0.5f

    internal fun dragBy(dy: Float) {
        settling?.cancel()
        fraction = (fraction - dy / height.coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    internal fun release(velocity: Float) = settle(
        when {
            velocity < -500f -> true
            velocity > 500f -> false
            else -> fraction > 0.4f
        },
    )

    fun settle(show: Boolean) {
        settling?.cancel()
        settling = scope.launch {
            androidx.compose.animation.core.animate(
                fraction, if (show) 1f else 0f,
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 420f),
            ) { v, _ -> fraction = v.coerceIn(0f, 1f) }
        }
    }

    fun toggle() = settle(!open)
}

@Composable
fun rememberReveal(): Reveal {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return androidx.compose.runtime.remember { Reveal(scope) }
}

/** Swipe up on this to pull the extras out, down to tuck them away. */
@Composable
fun Modifier.revealDrag(reveal: Reveal): Modifier = draggable(
    androidx.compose.foundation.gestures.rememberDraggableState { reveal.dragBy(it) },
    androidx.compose.foundation.gestures.Orientation.Vertical,
    onDragStopped = { reveal.release(it) },
)

/** Holds content that grows into view as [reveal] opens: only its layout and alpha change per frame. */
@Composable
fun RevealBox(reveal: Reveal, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                reveal.height = placeable.height
                // The settle spring can overshoot a hair past either end; a negative height crashes.
                val h = (placeable.height * reveal.fraction.coerceIn(0f, 1f)).roundToInt()
                layout(placeable.width, h) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                alpha = (reveal.fraction * 1.6f - 0.3f).coerceIn(0f, 1f)
                translationY = (1f - reveal.fraction) * 24.dp.toPx()
            },
    ) {
        content()
    }
}

/** The little bar that says "there's more under here"; tap it too. */
@Composable
fun RevealHandle(reveal: Reveal, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(18.dp)
            .pointerInput(Unit) { detectTapGestures { reveal.toggle() } }
            .semantics { contentDescription = if (reveal.open) "Hide volume and speed" else "Show volume and speed" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    // Widens a little as it opens.
                    scaleX = 1f + reveal.fraction * 0.4f
                }
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .drawBehind { drawRect(color) },
        )
    }
}
