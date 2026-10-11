package app.wave.music.ui.player

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import app.wave.music.playback.PlayerConnection
import app.wave.music.playback.BeatBus
import app.wave.music.data.WaveSettings
import app.wave.music.playback.PlayerUiState
import app.wave.music.ui.components.BlurredImage
import app.wave.music.ui.components.blocksTouchesBelow
import app.wave.music.ui.menu.LocalItemMenu
import app.wave.music.ui.menu.MenuTarget
import app.wave.music.ui.components.ProgressiveBackdropBlur
import app.wave.music.ui.components.backdropSource
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import app.wave.music.ui.components.ProgressiveBlurImage
import app.wave.music.ui.components.rememberArtColorScheme
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/** Full-art Now Playing (design board 10). */
@Composable
fun NowPlayingScreen(state: PlayerUiState, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val song = state.current ?: return
    val context = LocalContext.current
    var position by remember { mutableLongStateOf(0L) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    LaunchedEffect(song.videoId) {
        while (true) {
            position = PlayerConnection.position
            delay(200)
        }
    }

    MaterialTheme(colorScheme = rememberArtColorScheme(song.artworkUrl)) {
        val c = MaterialTheme.colorScheme
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .blocksTouchesBelow()
                .background(c.background),
        ) {
            val artHeight = maxHeight * 0.62f
            val artPx = with(LocalDensity.current) { artHeight.toPx() }
            // Art fills the top and blurs progressively; a mirrored, fully blurred copy continues it
            // under the controls, so there's no edge where the picture ends.
            val topBlur = rememberGraphicsLayer()
            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box(
                Modifier
                    .fillMaxSize()
                    .backdropSource(topBlur),
            ) {
                BlurredImage(
                    song.artworkUrl,
                    Modifier
                        .offset(y = artHeight)
                        .fillMaxWidth()
                        .height(artHeight)
                        .graphicsLayer { scaleY = -1f },
                    radius = 72.dp,
                )
                ProgressiveBlurImage(
                    song.artworkUrl,
                    Modifier
                        .fillMaxWidth()
                        .height(artHeight),
                    start = 0.58f,
                    end = 1f,
                    maxRadius = 72.dp,
                )
            }
            // Frosted top so "Playing from" always reads, whatever the art.
            ProgressiveBackdropBlur(topBlur, Modifier.fillMaxWidth().height(statusTop + 110.dp))
            // Darken toward the controls so text stays readable, keeping the song's color.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.3f),
                            0.12f to Color.Transparent,
                            0.38f to Color.Transparent,
                            0.62f to c.background.copy(alpha = 0.55f),
                            1f to c.background.copy(alpha = 0.82f),
                        ),
                    ),
            )

            Row(
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                GlassCircle(Icons.Rounded.KeyboardArrowDown, "Collapse player", onClose)
                state.context?.let {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text("PLAYING FROM", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                        Text(it.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val menu = LocalItemMenu.current
                GlassCircle(Icons.Rounded.MoreVert, "More options") {
                    menu.open(MenuTarget.Song(song.videoId, song.title, song.artist, song.artworkUrl))
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(song.title, color = c.onBackground, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, color = c.onSurfaceVariant, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (state.liked) c.primary else c.primaryContainer)
                                .clickable { PlayerConnection.toggleLike() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (state.liked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                                contentDescription = if (state.liked) "Liked" else "Like",
                                tint = if (state.liked) c.onPrimary else c.onPrimaryContainer,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    WavyProgress(
                        progress = if (state.durationMs > 0) position.toFloat() / state.durationMs else 0f,
                        playing = state.isPlaying,
                        energy = { if (WaveSettings.state.value.moveWithMusic) BeatBus.level.value else -1f },
                        color = c.onBackground,
                        trackColor = c.onBackground.copy(alpha = 0.25f),
                        stroke = 5.dp,
                        amplitude = 4.dp,
                        wavelength = 24.dp,
                        showThumb = true,
                        onSeek = { PlayerConnection.seekTo((it * state.durationMs).toLong()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(position), color = c.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("−" + formatTime(state.durationMs - position), color = c.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Connected group: previous · play/pause · next
                    Row(
                        Modifier
                            .padding(top = 14.dp)
                            .fillMaxWidth()
                            .height(92.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        GroupButton(Icons.Rounded.SkipPrevious, "Previous song", RoundedCornerShape(46.dp, 14.dp, 14.dp, 46.dp), c.secondaryContainer, c.onSecondaryContainer, 84) { PlayerConnection.previous() }
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(30.dp))
                                .background(c.primary)
                                .clickable { PlayerConnection.togglePlay() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = c.onPrimary,
                                modifier = Modifier.size(46.dp),
                            )
                        }
                        GroupButton(Icons.Rounded.SkipNext, "Next song", RoundedCornerShape(14.dp, 46.dp, 46.dp, 14.dp), c.secondaryContainer, c.onSecondaryContainer, 84) { PlayerConnection.next() }
                    }

                    // Labeled play modes
                    Row(
                        Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                            .height(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ModeButton(
                            Icons.Rounded.Shuffle,
                            if (state.shuffle) "Shuffle on" else "Shuffle off",
                            on = state.shuffle,
                            modifier = Modifier.weight(1f),
                        ) { PlayerConnection.toggleShuffle() }
                        ModeButton(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            when (state.repeatMode) {
                                Player.REPEAT_MODE_ALL -> "Repeat all"
                                Player.REPEAT_MODE_ONE -> "Repeat one"
                                else -> "Repeat off"
                            },
                            on = state.repeatMode != Player.REPEAT_MODE_OFF,
                            modifier = Modifier.weight(1f),
                        ) { PlayerConnection.cycleRepeat() }
                    }
                }

                // Bottom tray: what's next + lyrics
                // Song-colored tray that runs down behind the gesture bar.
                Row(
                    Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(c.primaryContainer.copy(alpha = 0.92f))
                        .navigationBarsPadding()
                        .height(68.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // What actually plays next, shuffle included.
                    val next = state.upcoming.firstOrNull()?.let(state.queue::getOrNull)
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showQueue = true }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = c.onPrimaryContainer)
                        Column {
                            Text("Up next", color = c.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                next?.let { "${it.title} · ${it.artist}" } ?: "End of queue",
                                color = c.onPrimaryContainer.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Box(Modifier.width(1.dp).height(32.dp).background(c.onPrimaryContainer.copy(alpha = 0.2f)))
                    Row(
                        Modifier
                            .width(128.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showLyrics = true }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Lyrics, null, tint = c.onPrimaryContainer)
                        Text("  Lyrics", color = c.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
        AnimatedVisibility(visible = showQueue, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            QueueScreen(state, onClose = { showQueue = false })
        }
        AnimatedVisibility(visible = showLyrics, enter = fadeIn(), exit = fadeOut()) {
            LyricsScreen(state, onClose = { showLyrics = false })
        }
    }
}

@Composable
private fun GlassCircle(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White) }
}

@Composable
private fun ModeButton(icon: ImageVector, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = if (on) RoundedCornerShape(18.dp) else RoundedCornerShape(24.dp)
    Row(
        modifier
            .fillMaxSize()
            .clip(shape)
            .then(if (on) Modifier.background(c.primary) else Modifier.border(1.dp, c.onBackground.copy(alpha = 0.2f), shape))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (on) c.onPrimary else c.onBackground)
        Text("  $label", color = if (on) c.onPrimary else c.onBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
