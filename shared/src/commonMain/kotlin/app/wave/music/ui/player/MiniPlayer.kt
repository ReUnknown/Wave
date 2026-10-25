package app.wave.music.ui.player

import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.core.Animatable
import app.wave.music.ui.components.ArtSize
import app.wave.music.ui.components.atSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.playback.PlayerConnection
import app.wave.music.playback.BeatBus
import app.wave.music.data.WaveSettings
import app.wave.music.playback.PlayerUiState
import app.wave.music.ui.components.BlurredImage
import app.wave.music.ui.components.PlayPauseIcon
import app.wave.music.ui.components.WaveMotion
import app.wave.music.ui.components.pressable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

/** Floating mini player: the song's own art, blurred, is the surface. */
@Composable
fun MiniPlayer(state: PlayerUiState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val song = state.current ?: return
    val position = rememberPosition(song.videoId, state.isPlaying, everyMs = 250)
    val shape = RoundedCornerShape(32.dp)
    val haptics = LocalHapticFeedback.current
    val desktop = app.wave.music.ui.components.LocalDesktop.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // Swipes (phones and tablets): sideways stops the music, up opens the player.
    val dx = remember { Animatable(0f) }
    val dy = remember { Animatable(0f) }
    val openAt = with(density) { 48.dp.toPx() }
    val gestures = if (desktop) Modifier else Modifier.pointerInput(Unit) {
        // Like a Pixel notification: swipe it off to the side (either way) to stop the music.
        // Swipe up opens the player.
        var axis = 0 // 0 = undecided, 1 = sideways, 2 = up/down
        val velocity = VelocityTracker()
        detectDragGestures(
            onDragStart = {
                axis = 0
                velocity.resetTracking()
            },
            onDragCancel = { scope.launch { launch { dx.animateTo(0f, WaveMotion.bouncy()) }; dy.animateTo(0f, WaveMotion.bouncy()) } },
            onDragEnd = {
                val flingX = velocity.calculateVelocity().x
                scope.launch {
                    val far = kotlin.math.abs(dx.value) > size.width * 0.35f
                    val flung = kotlin.math.abs(flingX) > 1_500f && kotlin.math.sign(flingX) == kotlin.math.sign(dx.value)
                    when {
                        axis == 1 && (far || flung) -> {
                            // Off it goes, the way you swiped, and the music stops.
                            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            val dir = if (dx.value > 0) 1f else -1f
                            dx.animateTo(dir * size.width * 1.1f, tween(180, easing = WaveMotion.EmphasizedAccelerate))
                            PlayerConnection.dismiss()
                            dx.snapTo(0f)
                        }
                        axis == 2 && dy.value < -openAt -> {
                            onOpen()
                            dy.animateTo(0f, WaveMotion.bouncy())
                        }
                        else -> {
                            launch { dx.animateTo(0f, WaveMotion.bouncy()) }
                            dy.animateTo(0f, WaveMotion.bouncy())
                        }
                    }
                }
            },
        ) { change, drag ->
            change.consume()
            velocity.addPosition(change.uptimeMillis, change.position)
            if (axis == 0) axis = if (kotlin.math.abs(drag.x) > kotlin.math.abs(drag.y)) 1 else 2
            scope.launch {
                if (axis == 1) dx.snapTo(dx.value + drag.x)
                // Up follows (to open the player); down just stretches a little and springs back.
                else dy.snapTo(dy.value + if (drag.y > 0 && dy.value >= 0f) drag.y * 0.25f else drag.y * 0.6f)
            }
        }
    }
    Box(
        Modifier
            .offset { IntOffset(dx.value.roundToInt(), dy.value.roundToInt()) }
            .graphicsLayer {
                // Fades as it's swiped off to the side, like a notification.
                alpha = 1f - (kotlin.math.abs(dx.value) / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.9f)
            }
            .then(gestures)
            .then(modifier)
            .fillMaxWidth()
            .height(84.dp)
            .shadow(16.dp, shape)
            .pressable(shape, pressedScale = 0.97f, onClick = onOpen)
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape),
    ) {
        Crossfade(song.artworkUrl?.atSize(ArtSize.BLUR), Modifier.matchParentSize(), animationSpec = tween(320), label = "miniBackdrop") { art ->
            BlurredImage(art, Modifier.fillMaxSize(), radius = 28.dp)
        }
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.6f)))),
        )
        Row(
            Modifier
                .fillMaxHeight()
                .padding(start = 10.dp, end = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Crossfade(song.artworkUrl?.atSize(ArtSize.THUMB), animationSpec = tween(260), label = "miniArt") { art ->
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(62.dp)
                        .clip(RoundedCornerShape(22.dp)),
                )
            }
            // A new song's name rolls up into place.
            AnimatedContent(
                targetState = song,
                contentKey = { it.videoId },
                transitionSpec = {
                    (slideInVertically(tween(260, easing = WaveMotion.EmphasizedDecelerate)) { it / 2 } + fadeIn(tween(160, delayMillis = 40))) togetherWith
                        (slideOutVertically(tween(160, easing = WaveMotion.EmphasizedAccelerate)) { -it / 2 } + fadeOut(tween(100)))
                },
                modifier = Modifier.weight(1f),
                label = "miniTitle",
            ) { shown ->
                Column {
                    Text(shown.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(shown.artist, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // Connected group: previous · pause · next
            Row(Modifier.height(52.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                GroupButton(Icons.Rounded.SkipPrevious, "Previous song", RoundedCornerShape(26.dp, 8.dp, 8.dp, 26.dp), Color.White.copy(alpha = 0.16f), Color.White, 40) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    PlayerConnection.previous()
                }
                // Paused, the middle button rounds out; the icon morphs between pause and play.
                val corner by animateDpAsState(if (state.isPlaying) 8.dp else 18.dp, WaveMotion.bouncy(), label = "miniPlayShape")
                Box(
                    Modifier
                        .width(56.dp)
                        .fillMaxHeight()
                        .pressable(RoundedCornerShape(corner), MaterialTheme.colorScheme.primary, pressedScale = 0.88f) {
                            haptics.performHapticFeedback(if (state.isPlaying) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                            PlayerConnection.togglePlay()
                        }
                        .semantics { contentDescription = if (state.isPlaying) "Pause" else "Play" },
                    contentAlignment = Alignment.Center,
                ) { PlayPauseIcon(state.isPlaying, MaterialTheme.colorScheme.onPrimary) }
                GroupButton(Icons.Rounded.SkipNext, "Next song", RoundedCornerShape(8.dp, 26.dp, 26.dp, 8.dp), Color.White.copy(alpha = 0.16f), Color.White, 40) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    PlayerConnection.next()
                }
                // Computers: no swiping, so a plain close button stops the music instead.
                if (desktop) {
                    Box(
                        Modifier
                            .padding(start = 6.dp)
                            .width(36.dp)
                            .fillMaxHeight()
                            .pressable(CircleShape, Color.White.copy(alpha = 0.1f), pressedScale = 0.88f) { PlayerConnection.dismiss() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Close, "Stop and close the player", tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
            }
        }
        WavyProgress(
            progress = { if (state.durationMs > 0) position.value.toFloat() / state.durationMs else 0f },
            playing = state.isPlaying,
            energy = { if (WaveSettings.state.value.moveWithMusic) BeatBus.level.value else -1f },
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f),
            stroke = 2.5.dp,
            amplitude = 2.dp,
            wavelength = 18.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 84.dp, end = 20.dp, bottom = 6.dp)
                .fillMaxWidth()
                .height(8.dp),
        )
    }
}

@Composable
internal fun GroupButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    shape: RoundedCornerShape,
    container: Color,
    content: Color,
    widthDp: Int,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .width(widthDp.dp)
            .fillMaxHeight()
            .pressable(shape, container, pressedScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = content) }
}
