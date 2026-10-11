package app.wave.music.ui.player

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
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

/** Floating mini player: the song's own art, blurred, is the surface. */
@Composable
fun MiniPlayer(state: PlayerUiState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val song = state.current ?: return
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(song.videoId, state.isPlaying) {
        while (true) {
            position = PlayerConnection.position
            delay(250)
        }
    }
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(84.dp)
            .shadow(16.dp, shape)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .clickable(onClick = onOpen),
    ) {
        BlurredImage(song.artworkUrl, Modifier.matchParentSize(), radius = 28.dp)
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
            AsyncImage(
                model = song.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(62.dp)
                    .clip(RoundedCornerShape(22.dp)),
            )
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Connected group: previous · pause · next
            Row(Modifier.height(52.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                GroupButton(Icons.Rounded.SkipPrevious, "Previous song", RoundedCornerShape(26.dp, 8.dp, 8.dp, 26.dp), Color.White.copy(alpha = 0.16f), Color.White, 40) { PlayerConnection.previous() }
                GroupButton(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (state.isPlaying) "Pause" else "Play",
                    RoundedCornerShape(8.dp),
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.onPrimary,
                    56,
                ) { PlayerConnection.togglePlay() }
                GroupButton(Icons.Rounded.SkipNext, "Next song", RoundedCornerShape(8.dp, 26.dp, 26.dp, 8.dp), Color.White.copy(alpha = 0.16f), Color.White, 40) { PlayerConnection.next() }
            }
        }
        WavyProgress(
            progress = if (state.durationMs > 0) position.toFloat() / state.durationMs else 0f,
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
            .clip(shape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = content) }
}
