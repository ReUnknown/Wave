package app.wave.music.ui.player

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
    Box(
        modifier
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
