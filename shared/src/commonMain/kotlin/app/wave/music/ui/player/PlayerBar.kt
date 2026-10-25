package app.wave.music.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.playback.RepeatMode
import app.wave.music.data.WaveSettings
import app.wave.music.playback.BeatBus
import app.wave.music.playback.PlayerConnection
import app.wave.music.playback.PlayerUiState
import app.wave.music.ui.components.ArtSize
import app.wave.music.ui.components.BlurredImage
import app.wave.music.ui.components.PlayPauseIcon
import app.wave.music.ui.components.WaveMotion
import app.wave.music.ui.components.atSize
import app.wave.music.ui.components.pressable
import coil3.compose.AsyncImage

/**
 * Computers and tablets: the player as a bar across the bottom of the window (design board 1),
 * with the song's art blurred behind it like the phone's mini player. The song on the left,
 * the controls and progress in the middle, lyrics, up next, volume and expand on the right.
 */
@Composable
fun PlayerBar(
    state: PlayerUiState,
    onOpen: () -> Unit,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
    /** Being dragged to a side (it fades back while a card of the song follows your finger). */
    lifted: Boolean = false,
    /** Tablets: drag the bar up toward a side to dock the player there. Gets the finger's window position. */
    onDockDrag: ((Offset) -> Unit)? = null,
    /** The drag ended: [dropped] is false when it was cancelled. */
    onDockEnd: (dropped: Boolean) -> Unit = {},
) {
    val song = state.current ?: return
    val position = rememberPosition(song.videoId, state.isPlaying, everyMs = 250)
    var origin by remember { mutableStateOf(Offset.Zero) }
    val fade by animateFloatAsState(if (lifted) 0.35f else 1f, tween(160), label = "barLift")
    Box(
        Modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .then(
                if (onDockDrag == null) Modifier else Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onDockDrag(origin + it) },
                        onDragEnd = { onDockEnd(true) },
                        onDragCancel = { onDockEnd(false) },
                    ) { change, _ ->
                        change.consume()
                        onDockDrag(origin + change.position)
                    }
                },
            )
            .graphicsLayer { alpha = fade }
            .then(modifier)
            .fillMaxWidth()
            .background(Color.Black),
    ) {
        Crossfade(song.artworkUrl?.atSize(ArtSize.BLUR), Modifier.matchParentSize(), animationSpec = tween(320), label = "barArt") { art ->
            BlurredImage(art, Modifier.fillMaxSize(), radius = 40.dp)
        }
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.42f), Color.Black.copy(alpha = 0.58f), Color.Black.copy(alpha = 0.42f)))))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))

        Row(
            Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(96.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // The song: tap it to open the player.
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpen)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AsyncImage(
                    song.artworkUrl?.atSize(ArtSize.THUMB),
                    null,
                    Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.weight(1f, fill = false)) {
                    Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BarIcon(
                    if (state.liked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                    if (state.liked) "Liked" else "Like",
                    tint = if (state.liked) MaterialTheme.colorScheme.primary else Color.White,
                ) { PlayerConnection.toggleLike() }
            }

            // Controls and progress.
            Column(Modifier.widthIn(min = 360.dp, max = 560.dp).weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BarIcon(Icons.Rounded.Shuffle, if (state.shuffle) "Shuffle on" else "Shuffle off", tint = if (state.shuffle) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.75f)) {
                        PlayerConnection.toggleShuffle()
                    }
                    Row(Modifier.height(44.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        GroupButton(Icons.Rounded.SkipPrevious, "Previous song", RoundedCornerShape(22.dp, 8.dp, 8.dp, 22.dp), Color.White.copy(alpha = 0.16f), Color.White, 52) { PlayerConnection.previous() }
                        val corner by animateDpAsState(if (state.isPlaying) 12.dp else 22.dp, WaveMotion.bouncy(), label = "barPlay")
                        Box(
                            Modifier
                                .width(76.dp)
                                .fillMaxHeight()
                                .pressable(RoundedCornerShape(corner), MaterialTheme.colorScheme.primary, pressedScale = 0.9f) { PlayerConnection.togglePlay() }
                                .semantics { contentDescription = if (state.isPlaying) "Pause" else "Play" },
                            contentAlignment = Alignment.Center,
                        ) { PlayPauseIcon(state.isPlaying, MaterialTheme.colorScheme.onPrimary) }
                        GroupButton(Icons.Rounded.SkipNext, "Next song", RoundedCornerShape(8.dp, 22.dp, 22.dp, 8.dp), Color.White.copy(alpha = 0.16f), Color.White, 52) { PlayerConnection.next() }
                    }
                    BarIcon(
                        if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        when (state.repeatMode) {
                            RepeatMode.ALL -> "Repeat all"
                            RepeatMode.ONE -> "Repeat one"
                            else -> "Repeat off"
                        },
                        tint = if (state.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.75f),
                    ) { PlayerConnection.cycleRepeat() }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BarTime(position, 0L)
                    WavyProgress(
                        progress = { if (state.durationMs > 0) position.value.toFloat() / state.durationMs else 0f },
                        playing = state.isPlaying,
                        energy = { if (WaveSettings.state.value.moveWithMusic) BeatBus.level.value else -1f },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        stroke = 3.dp,
                        amplitude = 2.5.dp,
                        wavelength = 20.dp,
                        showThumb = true,
                        onSeek = { PlayerConnection.seekTo((it * state.durationMs).toLong()) },
                        modifier = Modifier.weight(1f).height(18.dp),
                    )
                    BarTime(position, state.durationMs)
                }
            }

            // Lyrics, up next, volume, expand.
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                BarIcon(Icons.Rounded.Lyrics, "Lyrics", onClick = onLyrics)
                BarIcon(Icons.AutoMirrored.Rounded.QueueMusic, "Up next", onClick = onQueue)
                SpeedButton(state.speed)
                SlimVolume(Color.White, Modifier.padding(end = 6.dp))
                BarIcon(Icons.Rounded.OpenInFull, "Open the player", onClick = onOpen)
            }
        }
    }
}

/** Elapsed (when [durationMs] is 0) or remaining time, updating once a second. */
@Composable
private fun BarTime(position: androidx.compose.runtime.State<Long>, durationMs: Long) {
    val seconds by remember(position) { androidx.compose.runtime.derivedStateOf { position.value / 1000 } }
    val ms = if (durationMs > 0) (durationMs - seconds * 1000).coerceAtLeast(0) else seconds * 1000
    val total = ms / 1000
    Text(
        (if (durationMs > 0) "−" else "") + "%d:%02d".format(total / 60, total % 60),
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun BarIcon(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .pressable(CircleShape, pressedScale = 0.86f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** Playback speed: shows the current speed; click for the choices. */
@Composable
private fun SpeedButton(speed: Float) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val label = if (speed == 1f) "1×" else "${speed.toString().trimEnd('0').trimEnd('.')}×"
    Box {
        Box(
            Modifier
                .height(32.dp)
                .pressable(CircleShape, Color.White.copy(alpha = if (speed == 1f) 0f else 0.16f), pressedScale = 0.9f) { open = true }
                .padding(horizontal = 10.dp)
                .semantics { contentDescription = "Playback speed $label" },
            contentAlignment = Alignment.Center,
        ) { Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        androidx.compose.material3.DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Text(
                            if (s == 1f) "Normal" else "${s.toString().trimEnd('0').trimEnd('.')}×",
                            fontWeight = if (kotlin.math.abs(s - speed) < 0.01f) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = { PlayerConnection.setSpeed(s); open = false },
                )
            }
        }
    }
}
